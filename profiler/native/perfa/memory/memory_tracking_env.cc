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
constexpr long kClassStartTag = 1;
// Start tag of all other instance objects
// This assume enough buffer for the number of classes that are in an
// application.
constexpr long kObjectStartTag = 1e6;

const char* kClassClass = "Ljava/lang/Class;";

// Wait time between sending alloc data to perfd/studio.
constexpr long kDataTransferIntervalNs = Clock::ms_to_ns(500);

// TODO looks like we are capped by a protobuf message size limit.
// Investigate whether smaller batches are good enough, or if we
// should tweak the limit for profilers.
constexpr int kDataBatchSize = 2000;
}

namespace profiler {

using proto::BatchAllocationSample;

// STL container memory tracking for Debug only.
std::atomic<int64_t> g_max_used[kMemTagCount];
std::atomic<int64_t> g_total_used[kMemTagCount];
const char* MemTagToString(MemTag tag) {
  switch (tag) {
    case kClassTagMap:
      return "ClassTagMap";
    case kClassGlobalRefs:
      return "ClassGlobalRefs";
    case kClassData:
      return "ClassData";
    default:
      return "Unknown";
  }
}

MemoryTrackingEnv* MemoryTrackingEnv::Instance(JavaVM* vm) {
  if (g_env == nullptr) {
    // Create a stand-alone jvmtiEnv to avoid any callback conflicts
    // with other profilers' agents.
    g_vm = vm;
    jvmtiEnv* jvmti = CreateJvmtiEnv(g_vm);
    g_env = new MemoryTrackingEnv(jvmti);
    g_env->Initialize();
  }

  return g_env;
}

MemoryTrackingEnv::MemoryTrackingEnv(jvmtiEnv* jvmti)
    : jvmti_(jvmti),
      is_live_tracking_(false),
      app_id_(getpid()),
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

  auto memory_component = Agent::Instance().memory_component();
  memory_component->RegisterMemoryControlHandler(std::bind(
      &MemoryTrackingEnv::HandleControlSignal, this, std::placeholders::_1));
  memory_component->OpenControlStream();
}

/**
 * Starts live allocation tracking. The initialization process involves:
 * - Hooks on requried callbacks for alloc tracking
 * - Tagging all classes that are already loaded
 * - Walk through the heap to tag all existing objects
 * - Sets up a agent thread which offload data back to perfd/studio.
 */
void MemoryTrackingEnv::StartLiveTracking(int64_t timestamp) {
  if (is_live_tracking_) {
    return;
  }
  is_live_tracking_ = true;
  current_capture_time_ns_ = timestamp;
  event_queue_.Reset();

  // Called from grpc so we need to attach.
  JNIEnv* jni = GetThreadLocalJNI(g_vm);
  jvmtiError error;

  // Trigger a GC - this is necessary to clean up any Class objects
  // that are still left behind from the ClassLoad stage, which
  // we would not get from the GetLoadedClasses below, and we don't
  // care about them being on the heap.
  error = jvmti_->ForceGarbageCollection();
  CheckJvmtiError(jvmti_, error);

  // Enable ClassPrepare before hand, to avoid potential race
  // between tagging all loaded classes and iterating through the heap
  // below.
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE);

  // Tag all loaded classes and send to perfd.
  jint class_count = 0;
  jclass* classes;
  error = jvmti_->GetLoadedClasses(&class_count, &classes);
  CheckJvmtiError(jvmti_, error);
  BatchAllocationSample class_sample;
  class_sample.set_process_id(app_id_);
  for (int i = 0; i < class_count; ++i) {
    ScopedLocalRef<jclass> klass(jni, classes[i]);

    AllocationEvent* event = class_sample.add_events();
    RegisterNewClass(jni, klass.get(), event);
    event->set_capture_time(current_capture_time_ns_);
    event->set_timestamp(current_capture_time_ns_);
    if (class_sample.events_size() >= kDataBatchSize) {
      profiler::EnqueueAllocationEvents(class_sample);
      class_sample.clear_events();
    }
  }
  if (class_sample.events_size() > 0) {
    profiler::EnqueueAllocationEvents(class_sample);
  }
  Log::V("Loaded classes: %d", class_count);
  Deallocate(jvmti_, classes);

  // Tag all objects already allocated on the heap.
  BatchAllocationSample snapshot_sample;
  snapshot_sample.set_process_id(app_id_);
  jvmtiHeapCallbacks heap_callbacks;
  memset(&heap_callbacks, 0, sizeof(heap_callbacks));
  heap_callbacks.heap_iteration_callback = &HeapIterationCallback;
  error =
      jvmti_->IterateThroughHeap(0, nullptr, &heap_callbacks, &snapshot_sample);
  CheckJvmtiError(jvmti_, error);
  if (snapshot_sample.events_size() > 0) {
    snapshot_sample.set_timestamp(clock_.GetCurrentTime());
    profiler::EnqueueAllocationEvents(snapshot_sample);
  }
  Log::V("Live objects on heap: %ld", current_object_tag_ - kObjectStartTag);

  // Enable allocation+deallocation callbacks after initial heap walk.
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_VM_OBJECT_ALLOC);
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_OBJECT_FREE);

  // Start AllocWorkerThread
  error =
      jvmti_->RunAgentThread(AllocateJavaThread(jvmti_, jni), &AllocDataWorker,
                             this, JVMTI_THREAD_MAX_PRIORITY);
  CheckJvmtiError(jvmti_, error);
}

/**
 * TODO: Stops live allocation tracking by disabling the event notification
 * and removing all global refs that we have created for jclasses.
 */
void MemoryTrackingEnv::StopLiveTracking(int64_t timestamp) {}

void MemoryTrackingEnv::RegisterNewClass(JNIEnv* jni, jclass klass,
                                         AllocationEvent* event) {
  std::lock_guard<std::mutex> lock(class_data_mutex_);

  jvmtiError error;

  char* sig_mutf8;
  error = jvmti_->GetClassSignature(klass, &sig_mutf8, nullptr);
  CheckJvmtiError(jvmti_, error);
  // TODO this is wrong. We need to parse mutf-8.
  std::string klass_name = sig_mutf8;
  Deallocate(jvmti_, sig_mutf8);

  long tag = 0;
  AllocationEvent::Klass klass_data;
  auto itr = class_tag_map_.find(klass_name);
  if (itr != class_tag_map_.end()) {
    // We have a class from multiple class loaders.
    // TODO treat them as separate classes.
    // For now, let them share the same tag.
    tag = itr->second;
    klass_data.set_tag(tag);
    klass_data.set_name(klass_name);

  } else {
    tag = GetNextClassTag();
    klass_data.set_tag(tag);
    klass_data.set_name(klass_name);
    class_tag_map_.emplace(std::make_pair(klass_name, tag));
    class_data_.push_back(klass_data);
    assert(class_data_.size() == tag);
  }
  error = jvmti_->SetTag(klass, tag);
  CheckJvmtiError(jvmti_, error);

  // Cache the jclasses so that they will never be gc.
  // This ensures that any jmethodID/jfieldID will never become
  // invalid.
  // TODO: Investigate any memory implications - presumably
  // the number of classes won't be enormous. (e.g. < 1e6)
  class_global_refs_.push_back(jni->NewGlobalRef(klass));

  // Copy the klass_data over to the AllocationEvent to
  // be sent to perfd.
  event->mutable_class_data()->CopyFrom(klass_data);
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
           (long)g_total_used[i].load(), (long)g_max_used[i].load());
  }
  event_queue_.PrintStats();
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

  assert(user_data != nullptr);
  assert(class_tag != 0);  // All classes should be tagged by this point.
  assert(g_env->class_data_.size() >= class_tag);

  // Class tag starts at 1, thus the offset in its vector position.
  const auto class_data = g_env->class_data_[class_tag - 1];
  if (class_data.name().compare(kClassClass) == 0) {
    // Skip Class objects as they should already be tagged.
    // TODO account for their sizes in Ljava/lang/Class;
    // Alternatively, perform the bookkeeping on Studio side.
    assert(*tag_ptr != 0);
    return JVMTI_VISIT_OBJECTS;
  }

  long tag = g_env->GetNextObjectTag();
  *tag_ptr = tag;

  AllocationEvent* event = sample->add_events();
  event->set_capture_time(g_env->current_capture_time_ns_);
  event->set_timestamp(g_env->current_capture_time_ns_);

  AllocationEvent::Allocation* alloc = event->mutable_alloc_data();
  alloc->set_tag(tag);
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
  AllocationEvent event;
  g_env->RegisterNewClass(jni, klass, &event);
  event.set_capture_time(g_env->current_capture_time_ns_);
  event.set_timestamp(g_env->clock_.GetCurrentTime());
  g_env->event_queue_.Push(event);
}

void MemoryTrackingEnv::ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                            jthread thread, jobject object,
                                            jclass klass, jlong size) {
  g_env->total_live_count_++;

  jvmtiError error;

  char* sig_mutf8;
  error = jvmti->GetClassSignature(klass, &sig_mutf8, nullptr);
  CheckJvmtiError(jvmti, error);
  // TODO this is wrong. We need to parse mutf-8.
  std::string klass_name = sig_mutf8;
  Deallocate(jvmti, sig_mutf8);

  if (klass_name.compare(kClassClass) == 0) {
    // Special case, we can potentially get two allocation events
    // when a class is loaded: One for ClassLoad and another for
    // ClassPrepare. We don't know which one it is here, so opting
    // to handle Class object allocation in ClassPrepare instead.
    return;
  }

  long tag = g_env->GetNextObjectTag();
  error = jvmti->SetTag(object, tag);
  CheckJvmtiError(jvmti, error);

  auto itr = g_env->class_tag_map_.find(klass_name);
  assert(itr != g_env->class_tag_map_.end());
  Stopwatch sw;
  {
    AllocationEvent event;
    AllocationEvent::Allocation* alloc_data = event.mutable_alloc_data();
    alloc_data->set_tag(tag);
    alloc_data->set_size(size);
    alloc_data->set_class_tag(itr->second);
    event.set_capture_time(g_env->current_capture_time_ns_);
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
    event.set_capture_time(g_env->current_capture_time_ns_);
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

    BatchAllocationSample sample;
    sample.set_process_id(env->app_id_);

    // Gather all the data currently in the queue and push to perfd.
    // TODO: investigate whether we need to set time cap for large amount of
    // data.
    std::deque<AllocationEvent> queued_data = env->event_queue_.Drain();
    while (!queued_data.empty()) {
      AllocationEvent* event = sample.add_events();
      event->CopyFrom(queued_data.front());
      queued_data.pop_front();
      if (sample.events_size() >= kDataBatchSize) {
        profiler::EnqueueAllocationEvents(sample);
        sample.clear_events();
      }
    }

    if (sample.events_size() > 0) {
      profiler::EnqueueAllocationEvents(sample);
    }

    profiler::EnqueueAllocStats(env->total_live_count_, env->total_free_count_);

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

}  // namespace profiler
