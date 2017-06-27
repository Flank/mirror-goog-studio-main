/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "memory_tracking_env.h"

#include <cassert>
#include <cstring>
#include <deque>
#include <vector>

#include "agent/agent.h"
#include "agent/support/memory_stats_logger.h"
#include "perfa/jvmti_helper.h"
#include "perfa/scoped_local_ref.h"
#include "utils/clock.h"
#include "utils/log.h"
#include "utils/stopwatch.h"

namespace {

static JavaVM* g_vm;
static profiler::MemoryTrackingEnv* g_env;

// Start tag of Class objects - use 1 as 0 represents no tag.
constexpr int32_t kClassStartTag = 1;

// Start tag of all other instance objects
// This assume enough buffer for the number of classes that are in an
// application. (64K - 1 which is plenty?)
constexpr int32_t kObjectStartTag = 1 << 16;

const char* kClassClass = "Ljava/lang/Class;";

// Wait time between sending alloc data to perfd/studio.
constexpr int64_t kDataTransferIntervalNs = Clock::ms_to_ns(500);

// TODO looks like we are capped by a protobuf message size limit.
// Investigate whether smaller batches are good enough, or if we
// should tweak the limit for profilers.
constexpr int32_t kDataBatchSize = 2000;

// The max depth of callstacks to query per allocation.
constexpr int32_t kMaxStackDepth = 100;
}

namespace profiler {

using proto::AllocationStack;
using proto::BatchAllocationSample;
using proto::EncodedAllocationStack;

// STL container memory tracking for Debug only.
std::atomic<long> g_max_used[kMemTagCount];
std::atomic<long> g_total_used[kMemTagCount];
const char* MemTagToString(MemTag tag) {
  switch (tag) {
    case kClassTagMap:
      return "ClassTagMap";
    case kClassGlobalRefs:
      return "ClassGlobalRefs";
    case kClassData:
      return "ClassData";
    case kMethodIds:
      return "MethodIds";
    case kThreadIdMap:
      return "ThreadIdMap";
    default:
      return "Unknown";
  }
}

MemoryTrackingEnv* MemoryTrackingEnv::Instance(JavaVM* vm,
                                               bool log_live_alloc_count) {
  if (g_env == nullptr) {
    // Create a stand-alone jvmtiEnv to avoid any callback conflicts
    // with other profilers' agents.
    g_vm = vm;
    jvmtiEnv* jvmti = CreateJvmtiEnv(g_vm);
    g_env = new MemoryTrackingEnv(jvmti, log_live_alloc_count);
    g_env->Initialize();
  }

  return g_env;
}

MemoryTrackingEnv::MemoryTrackingEnv(jvmtiEnv* jvmti, bool log_live_alloc_count)
    : jvmti_(jvmti),
      log_live_alloc_count_(log_live_alloc_count),
      is_first_tracking_(true),
      is_live_tracking_(false),
      app_id_(getpid()),
      class_class_tag_(-1),
      current_capture_time_ns_(-1),
      last_gc_start_ns_(-1),
      total_live_count_(0),
      total_free_count_(0),
      current_class_tag_(kClassStartTag),
      current_object_tag_(kObjectStartTag) {}

void MemoryTrackingEnv::Initialize() {
  jvmtiError error;

  SetAllCapabilities(jvmti_);

  // Hook up event callbacks
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  // Note: we only track ClassPrepare as class information like
  // fields and methods are not yet available during ClassLoad.
  callbacks.ClassPrepare = &ClassPrepareCallback;
  callbacks.VMObjectAlloc = &ObjectAllocCallback;
  callbacks.ObjectFree = &ObjectFreeCallback;
  callbacks.GarbageCollectionStart = &GCStartCallback;
  callbacks.GarbageCollectionFinish = &GCFinishCallback;
  error = jvmti_->SetEventCallbacks(&callbacks, sizeof(callbacks));
  CheckJvmtiError(jvmti_, error);

  // Enable GC events always
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_START);
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_FINISH);

  Agent::Instance().memory_component().RegisterMemoryControlHandler(std::bind(
      &MemoryTrackingEnv::HandleControlSignal, this, std::placeholders::_1));
  Agent::Instance().memory_component().OpenControlStream();

  // Start AllocWorkerThread - this is alive for the duration of the agent, but
  // it only sends data when a tracking session is ongoing.
  JNIEnv* jni = GetThreadLocalJNI(g_vm);
  error =
      jvmti_->RunAgentThread(AllocateJavaThread(jvmti_, jni), &AllocDataWorker,
                             this, JVMTI_THREAD_MAX_PRIORITY);
  CheckJvmtiError(jvmti_, error);
}

/**
 * Starts live allocation tracking. The initialization process involves:
 * - Hooks on requried callbacks for alloc tracking
 * - Tagging all classes that are already loaded and send them to perfd
 * - Walk through the heap to tag all existing objects and send them to perfd
 *
 * Note - Each unique class share the same tag across sessions, while for
 * instance objects, they are retagged starting from |kObjectStartTag| on each
 * restart. This is because we aren't listening to free events in between
 * sessions, so we don't know which tag from a previous session is still alive
 * without caching an extra set to track what the agent has tagged.
 */
void MemoryTrackingEnv::StartLiveTracking(int64_t timestamp) {
  std::lock_guard<std::mutex> lock(tracking_mutex_);
  if (is_live_tracking_) {
    return;
  }
  is_live_tracking_ = true;
  current_capture_time_ns_ = timestamp;
  total_live_count_ = 0;
  total_free_count_ = 0;
  current_object_tag_ = kObjectStartTag;

  // Called from grpc so we need to attach.
  JNIEnv* jni = GetThreadLocalJNI(g_vm);
  jvmtiError error;
  {
    std::lock_guard<std::mutex> lock(class_data_mutex_);
    // If this is the first tracking session. Loop through all the already
    // loaded classes and tag/register them.
    if (is_first_tracking_) {
      is_first_tracking_ = false;

      // Enable ClassPrepare beforehand which allows us to capture any
      // subsequent class loads not returned from GetLoadedClasses.
      SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE);

      jint class_count = 0;
      jclass* classes;
      error = jvmti_->GetLoadedClasses(&class_count, &classes);
      CheckJvmtiError(jvmti_, error);
      for (int i = 0; i < class_count; ++i) {
        ScopedLocalRef<jclass> klass(jni, classes[i]);
        RegisterNewClass(jvmti_, jni, klass.get());
      }
      Log::V("Loaded classes: %d", class_count);
      Deallocate(jvmti_, classes);

      // Should have found java/lang/Class at this point.
      assert(class_class_tag_ != -1);
    }

    // Send back class data at the beginning of each session. De-duping needs to
    // be done by the caller as class tags remain unique throughout the app.
    // TODO: Only send back new classes since the last tracking session.
    // Note: The Allocation event associated with each class is sent during the
    // initial heap walk.
    BatchAllocationSample class_sample;
    for (const AllocatedClass& klass : class_data_) {
      AllocationEvent* event = class_sample.add_events();
      event->mutable_class_data()->CopyFrom(klass);
      event->set_timestamp(current_capture_time_ns_);
      if (class_sample.events_size() >= kDataBatchSize) {
        profiler::EnqueueAllocationEvents(class_sample);
        class_sample = BatchAllocationSample();
      }
    }
    if (class_sample.events_size() > 0) {
      profiler::EnqueueAllocationEvents(class_sample);
    }
  }

  // Tag all objects already allocated on the heap.
  BatchAllocationSample snapshot_sample;
  jvmtiHeapCallbacks heap_callbacks;
  memset(&heap_callbacks, 0, sizeof(heap_callbacks));
  heap_callbacks.heap_iteration_callback = &HeapIterationCallback;
  error =
      jvmti_->IterateThroughHeap(0, nullptr, &heap_callbacks, &snapshot_sample);
  CheckJvmtiError(jvmti_, error);
  if (snapshot_sample.events_size() > 0) {
    profiler::EnqueueAllocationEvents(snapshot_sample);
  }

  // Enable allocation+deallocation callbacks after initial heap walk.
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_VM_OBJECT_ALLOC);
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_OBJECT_FREE);
}

/**
 * Stops live allocation tracking.
 * - Disable allocation callbacks and clear the queued allocation events.
 * - Class/Method/Stack data are kept around so they can be referenced across
 *   tracking sessions.
 */
void MemoryTrackingEnv::StopLiveTracking(int64_t timestamp) {
  std::lock_guard<std::mutex> lock(tracking_mutex_);
  if (!is_live_tracking_) {
    return;
  }
  is_live_tracking_ = false;
  SetEventNotification(jvmti_, JVMTI_DISABLE, JVMTI_EVENT_VM_OBJECT_ALLOC);
  SetEventNotification(jvmti_, JVMTI_DISABLE, JVMTI_EVENT_OBJECT_FREE);
  event_queue_.Reset();
}

const AllocatedClass& MemoryTrackingEnv::RegisterNewClass(jvmtiEnv* jvmti,
                                                          JNIEnv* jni,
                                                          jclass klass) {
  jvmtiError error;

  ClassInfo klass_info;
  GetClassInfo(g_env, jvmti, jni, klass, &klass_info);
  auto itr = class_tag_map_.find(klass_info);

  // It is possible to see the same class from same class loader. This can
  // happen during the tracking intiailization process, where there can be a
  // race between GetLoadedClasses and the ClassPrepare callback, and the same
  // class object calls into this method from both places.
  bool new_klass = itr == class_tag_map_.end();
  int32_t tag = new_klass ? GetNextClassTag() : itr->second;
  if (new_klass) {
    AllocatedClass klass_data;
    klass_data.set_class_id(tag);
    klass_data.set_class_name(klass_info.class_name);
    klass_data.set_class_loader_id(klass_info.class_loader_id);
    class_tag_map_.emplace(std::make_pair(klass_info, tag));
    class_data_.push_back(klass_data);
    assert(class_data_.size() == tag);

    error = jvmti->SetTag(klass, tag);
    CheckJvmtiError(jvmti, error);

    // Cache the class object so that they will never be gc.
    // This ensures that any jmethodID/jfieldID will never become invalid.
    // TODO: Investigate any memory implications - presumably the number of
    // classes won't be enormous. (e.g. < (1<<16))
    class_global_refs_.push_back(jni->NewGlobalRef(klass));
  }

  if (klass_info.class_name.compare(kClassClass) == 0) {
    // Should only see java/lang/Class once.
    assert(class_class_tag_ == -1);
    class_class_tag_ = tag;
  }

  // Valid class tags start at 1, so -1 to get the valid index.
  return class_data_.at(tag - 1);
}

void MemoryTrackingEnv::LogGcStart() {
  last_gc_start_ns_ = clock_.GetCurrentTime();
}

void MemoryTrackingEnv::LogGcFinish() {
  profiler::EnqueueGcStats(last_gc_start_ns_, clock_.GetCurrentTime());

#ifndef NDEBUG
  Log::V(">> [MEM AGENT STATS DUMP BEGIN]");
  Log::V(">> Timing(ns)");
  for (int i = 0; i < TimingStats::kTimingTagCount; i++) {
    timing_stats_.Print(static_cast<TimingStats::TimingTag>(i));
  }
  Log::V(">> Memory(bytes)");
  for (int i = 0; i < kMemTagCount; i++) {
    Log::V(">> %s: Total=%ld, Max=%ld", MemTagToString((MemTag)i),
           g_total_used[i].load(), g_max_used[i].load());
  }
  event_queue_.PrintStats();
  stack_trie_.PrintStats();
  Log::V(">> [MEM AGENT STATS DUMP END]");
#endif
}

void MemoryTrackingEnv::HandleControlSignal(
    const MemoryControlRequest* request) {
  switch (request->control_case()) {
    case MemoryControlRequest::kEnableRequest:
      Log::V("Live memory tracking enabled.");
      StartLiveTracking(request->enable_request().timestamp());
      break;
    case MemoryControlRequest::kDisableRequest:
      Log::V("Live memory tracking disabled.");
      StopLiveTracking(request->disable_request().timestamp());
      break;
    default:
      Log::V("Unknown memory control signal.");
  }
}

jint MemoryTrackingEnv::HeapIterationCallback(jlong class_tag, jlong size,
                                              jlong* tag_ptr, jint length,
                                              void* user_data) {
  g_env->total_live_count_++;

  BatchAllocationSample* sample = (BatchAllocationSample*)user_data;

  assert(sample != nullptr);
  assert(class_tag != 0);  // All classes should be tagged by this point.
  assert(g_env->class_data_.size() >= class_tag);
  if (class_tag == g_env->class_class_tag_) {
    // Do not retag Class objects as they should already be tagged.
    // Note - we can have remnant Class objects from the ClassLoad phase, which
    // we would't see from GetLoadedClasses and would not be tagged. We don't
    // want to send AllocationEvent for them so simply ignore.
    if (*tag_ptr == 0) {
      return JVMTI_VISIT_OBJECTS;
    }
  } else {
    int32_t tag = g_env->GetNextObjectTag();
    *tag_ptr = tag;
  }

  AllocationEvent* event = sample->add_events();
  event->set_timestamp(g_env->current_capture_time_ns_);

  AllocationEvent::Allocation* alloc = event->mutable_alloc_data();
  alloc->set_tag(*tag_ptr);
  alloc->set_class_tag(class_tag);
  alloc->set_size(size);
  alloc->set_length(length);

  if (sample->events_size() >= kDataBatchSize) {
    profiler::EnqueueAllocationEvents(*sample);
    sample->clear_events();
  }

  return JVMTI_VISIT_OBJECTS;
}

void MemoryTrackingEnv::ClassPrepareCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                             jthread thread, jclass klass) {
  std::lock_guard<std::mutex> lock(g_env->class_data_mutex_);
  AllocationEvent klass_event;
  auto klass_data = g_env->RegisterNewClass(jvmti, jni, klass);
  klass_event.mutable_class_data()->CopyFrom(klass_data);
  klass_event.set_timestamp(g_env->clock_.GetCurrentTime());
  // Note, the same class could have been pushed during the GetLoadedClasses
  // logic already so this could be a duplicate. De-dup is done on Studio-side
  // database logic based on tag uniqueness.
  g_env->event_queue_.Push(klass_event);

  // Create and send a matching Allocation event for the class object.
  AllocationEvent alloc_event;
  AllocationEvent::Allocation* alloc_data = alloc_event.mutable_alloc_data();
  alloc_data->set_tag(klass_data.class_id());
  alloc_data->set_class_tag(g_env->class_class_tag_);
  // Need to get size manually as well...
  jlong size;
  jvmtiError error = jvmti->GetObjectSize(klass, &size);
  CheckJvmtiError(jvmti, error);
  alloc_data->set_size(size);
  // Fill thread + stack info.
  FillAllocEventThreadData(g_env, jvmti, jni, thread, alloc_data);
  alloc_event.set_timestamp(g_env->clock_.GetCurrentTime());
  // This can be duplicated as well and de-dup is done on Studio-side.
  g_env->event_queue_.Push(alloc_event);
}

void MemoryTrackingEnv::ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                            jthread thread, jobject object,
                                            jclass klass, jlong size) {
  g_env->total_live_count_++;

  jvmtiError error;

  ClassInfo klass_info;
  GetClassInfo(g_env, jvmti, jni, klass, &klass_info);
  if (klass_info.class_name.compare(kClassClass) == 0) {
    // Special case, we can potentially get two allocation events
    // when a class is loaded: One for ClassLoad and another for
    // ClassPrepare. We don't know which one it is here, so opting
    // to handle Class object allocation in ClassPrepare instead.
    return;
  }

  Stopwatch sw;
  {
    int32_t tag = g_env->GetNextObjectTag();
    error = jvmti->SetTag(object, tag);
    CheckJvmtiError(jvmti, error);

    auto itr = g_env->class_tag_map_.find(klass_info);
    assert(itr != g_env->class_tag_map_.end());
    AllocationEvent event;
    AllocationEvent::Allocation* alloc_data = event.mutable_alloc_data();
    alloc_data->set_tag(tag);
    alloc_data->set_size(size);
    alloc_data->set_class_tag(itr->second);
    FillAllocEventThreadData(g_env, jvmti, jni, thread, alloc_data);
    event.set_timestamp(g_env->clock_.GetCurrentTime());
    g_env->event_queue_.Push(event);
  }
  g_env->timing_stats_.Track(TimingStats::kAllocate, sw.GetElapsed());
}

void MemoryTrackingEnv::ObjectFreeCallback(jvmtiEnv* jvmti, jlong tag) {
  g_env->total_free_count_++;

  Stopwatch sw;
  {
    AllocationEvent event;
    AllocationEvent::Deallocation* free_data = event.mutable_free_data();
    free_data->set_tag(tag);
    // Associate the free event with the last gc that occurred.
    event.set_timestamp(g_env->last_gc_start_ns_);
    g_env->event_queue_.Push(event);
  }
  g_env->timing_stats_.Track(TimingStats::kFree, sw.GetElapsed());
}

void MemoryTrackingEnv::GCStartCallback(jvmtiEnv* jvmti) {
  g_env->LogGcStart();
}

void MemoryTrackingEnv::GCFinishCallback(jvmtiEnv* jvmti) {
  g_env->LogGcFinish();
}

void MemoryTrackingEnv::AllocDataWorker(jvmtiEnv* jvmti, JNIEnv* jni,
                                        void* ptr) {
  Stopwatch stopwatch;
  MemoryTrackingEnv* env = static_cast<MemoryTrackingEnv*>(ptr);
  assert(env != nullptr);
  while (true) {
    int64_t start_time_ns = stopwatch.GetElapsed();

    {
      std::lock_guard<std::mutex> lock(env->tracking_mutex_);
      if (env->is_live_tracking_) {
        BatchAllocationSample sample;
        // Gather all the data currently in the queue and push to perfd.
        // TODO: investigate whether we need to set time cap for large amount of
        // data.
        std::deque<AllocationEvent> queued_data = env->event_queue_.Drain();
        std::vector<int64_t> method_ids_to_query;
        while (!queued_data.empty()) {
          AllocationEvent* event = sample.add_events();
          event->CopyFrom(queued_data.front());
          queued_data.pop_front();

          switch (event->event_case()) {
            case AllocationEvent::kAllocData: {
              AllocationEvent::Allocation* alloc_data =
                  event->mutable_alloc_data();
              int stack_size = alloc_data->method_ids_size();
              assert(stack_size == alloc_data->location_ids_size());

              auto thread_itr =
                  env->thread_id_map_.find(alloc_data->thread_name());
              if (thread_itr == env->thread_id_map_.end()) {
                // New thread. Encode and sends the mapping along the sample.
                auto result = env->thread_id_map_.insert(std::make_pair(
                    alloc_data->thread_name(), env->thread_id_map_.size() + 1));
                thread_itr = result.first;
                proto::ThreadInfo* ti = sample.add_thread_infos();
                ti->set_timestamp(event->timestamp());
                ti->set_thread_id(thread_itr->second);
                ti->set_thread_name(thread_itr->first);
              }
              alloc_data->set_thread_id(thread_itr->second);
              alloc_data->clear_thread_name();

              // Store and encode the stack into trie.
              // TODO - consider moving trie storage to perfd?
              if (stack_size > 0) {
                std::vector<FrameInfo> reversed_stack(stack_size);
                for (int i = 0; i < stack_size; i++) {
                  int64_t method = alloc_data->method_ids(i);
                  int64_t location = alloc_data->location_ids(i);
                  reversed_stack[stack_size - i - 1] = {method, location};
                  if (env->known_method_ids_.emplace(method).second) {
                    method_ids_to_query.push_back(method);
                  }
                }

                auto result = env->stack_trie_.insert(reversed_stack);
                if (result.second) {
                  // Append the stack info into BatchAllocationSample
                  EncodedAllocationStack* encoded_stack = sample.add_stacks();
                  encoded_stack->set_timestamp(event->timestamp());
                  encoded_stack->set_stack_id(result.first);
                  // Yet reverse again so first entry is top of stack.
                  for (int j = stack_size - 1; j >= 0; j--) {
                    int32_t line_number =
                        FindLineNumber(env, jvmti, reversed_stack[j].method_id,
                                       reversed_stack[j].location_id);
                    encoded_stack->add_method_ids(reversed_stack[j].method_id);
                    encoded_stack->add_line_numbers(line_number);
                  }
                }

                // Only store the leaf index into alloc_data.
                // The full stack will be looked up from EncodedStack on
                // studio-side.
                alloc_data->clear_method_ids();
                alloc_data->clear_location_ids();
                alloc_data->set_stack_id(result.first);
              }
            } break;
            default:
              // Do nothing for Klass + Deallocation.
              break;
          }

          if (sample.events_size() >= kDataBatchSize) {
            SetSampleMethods(env, jvmti, jni, sample, method_ids_to_query);
            profiler::EnqueueAllocationEvents(sample);
            sample = BatchAllocationSample();
            method_ids_to_query.clear();
          }
        }

        if (sample.events_size() > 0) {
          SetSampleMethods(env, jvmti, jni, sample, method_ids_to_query);
          profiler::EnqueueAllocationEvents(sample);
        }

        if (env->log_live_alloc_count_) {
          profiler::EnqueueAllocStats(env->total_live_count_,
                                      env->total_free_count_);
        }
      }
    }

    // Sleeps a while before reading from the queue again, so that the agent
    // don't generate too many rpc requests in places with high allocation
    // frequency.
    int64_t elapsed_time_ns = stopwatch.GetElapsed() - start_time_ns;
    if (kDataTransferIntervalNs > elapsed_time_ns) {
      int64_t sleep_time_us =
          Clock::ns_to_us(kDataTransferIntervalNs - elapsed_time_ns);
      usleep(static_cast<uint64_t>(sleep_time_us));
    }
  }
}

void MemoryTrackingEnv::SetSampleMethods(
    MemoryTrackingEnv* env, jvmtiEnv* jvmti, JNIEnv* jni,
    BatchAllocationSample& sample, const std::vector<int64_t>& method_ids) {
  jvmtiError error;
  for (auto id : method_ids) {
    Stopwatch sw;
    {
      jmethodID method_id = reinterpret_cast<jmethodID>(id);

      char* method_name;
      error = jvmti->GetMethodName(method_id, &method_name, nullptr, nullptr);
      CheckJvmtiError(jvmti, error);

      jclass klass;
      error = jvmti->GetMethodDeclaringClass(method_id, &klass);
      CheckJvmtiError(jvmti, error);
      assert(klass != nullptr);

      ScopedLocalRef<jclass> scoped_klass(jni, klass);
      char* klass_name;
      error =
          jvmti->GetClassSignature(scoped_klass.get(), &klass_name, nullptr);

      AllocationStack::StackFrame* method = sample.add_methods();
      method->set_method_id(id);
      method->set_method_name(method_name);
      method->set_class_name(klass_name);

      Deallocate(jvmti, method_name);
      Deallocate(jvmti, klass_name);
    }
    env->timing_stats_.Track(TimingStats::kResolveCallstack, sw.GetElapsed());
  }
}

int32_t MemoryTrackingEnv::FindLineNumber(MemoryTrackingEnv* env,
                                          jvmtiEnv* jvmti, int64_t method_id,
                                          int64_t location_id) {
  int32_t line_number = -1;
  // Ignore native methods
  if (location_id != -1) {
    Stopwatch sw;
    jint entry_count = 0;
    jvmtiLineNumberEntry* line_number_entry = nullptr;
    jmethodID method = reinterpret_cast<jmethodID>(method_id);
    // TODO: Cache known line number tables?
    jvmtiError error =
        jvmti->GetLineNumberTable(method, &entry_count, &line_number_entry);
    if (CheckJvmtiError(jvmti, error, "Cannot get line number")) {
      return line_number;
    }

    for (int i = 0; i < entry_count; i++) {
      jvmtiLineNumberEntry entry = line_number_entry[i];
      if (entry.start_location > location_id) {
        break;
      }
      line_number = entry.line_number;
    }

    Deallocate(jvmti, line_number_entry);
    env->timing_stats_.Track(TimingStats::kResolveLineNumber, sw.GetElapsed());
  }

  return line_number;
}

void MemoryTrackingEnv::FillAllocEventThreadData(
    MemoryTrackingEnv* env, jvmtiEnv* jvmti, JNIEnv* jni, jthread thread,
    AllocationEvent::Allocation* alloc_data) {
  jvmtiError error;
  Stopwatch sw;
  {
    // Collect thread info
    jvmtiThreadInfo ti;
    error = jvmti->GetThreadInfo(thread, &ti);
    CheckJvmtiError(jvmti, error);
    ScopedLocalRef<jobject> scoped_thread_group(jni, ti.thread_group);
    ScopedLocalRef<jobject> scoped_class_loader(jni, ti.context_class_loader);
    alloc_data->set_thread_name(ti.name);
    Deallocate(jvmti, ti.name);
  }
  env->timing_stats_.Track(TimingStats::kThreadInfo, sw.GetElapsed());

  sw.Start();
  {
    // Collect stack frames
    jvmtiFrameInfo frames[kMaxStackDepth];
    jint count = 0;
    error = jvmti->GetStackTrace(thread, 0, kMaxStackDepth, frames, &count);
    CheckJvmtiError(jvmti, error);
    for (int i = 0; i < count; i++) {
      int64_t method_id = reinterpret_cast<int64_t>(frames[i].method);
      // jlocation is just a jlong.
      int64_t location_id = reinterpret_cast<jlong>(frames[i].location);
      alloc_data->add_method_ids(method_id);
      alloc_data->add_location_ids(location_id);
    }
  }
  env->timing_stats_.Track(TimingStats::kGetCallstack, sw.GetElapsed());
}

void MemoryTrackingEnv::GetClassInfo(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                                     JNIEnv* jni, jclass klass,
                                     ClassInfo* klass_info) {
  jvmtiError error;
  Stopwatch sw;
  {
    // Get class loader id.
    klass_info->class_loader_id = GetClassLoaderId(jvmti, jni, klass);

    // Get class name.
    char* sig_mutf8;
    error = jvmti->GetClassSignature(klass, &sig_mutf8, nullptr);
    CheckJvmtiError(jvmti, error);

    // TODO this is wrong. We need to parse mutf-8.
    klass_info->class_name = sig_mutf8;
    Deallocate(jvmti, sig_mutf8);
  }
  env->timing_stats_.Track(TimingStats::kClassInfo, sw.GetElapsed());
}
}  // namespace profiler
