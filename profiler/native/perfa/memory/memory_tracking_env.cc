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

#include <algorithm>
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
#include "utils/native_backtrace.h"
#include "utils/stopwatch.h"

namespace {

// Method signature for IterateThroughHeap extension that includes heap id.
// Note that the signature is almost identical to IterateThroughHeap, with the
// heap_iteration_callback in jvmtiHeapCallbacks taking a function pointer with
// an additional int parameter.
using IterateThroughHeapExt = jvmtiError (*)(jvmtiEnv*, jint, jclass,
                                             const jvmtiHeapCallbacks*,
                                             const void*);

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

// Line numbers are 1-based in Studio.
constexpr int32_t kInvalidLineNumber = 0;

// JVMTI extension method for querying per-object heap id
const char* kIterateHeapExtFunc =
    "com.android.art.heap.iterate_through_heap_ext";
static IterateThroughHeapExt g_iterate_heap_ext_func = nullptr;

// Getting the heap Id of an object (extenstion method) is an expensive
// call. We simply presumme everything allocated after the app starts
// belongs to the app heap.
constexpr int32_t kAppHeapId = 3;

// Sampling rate for full tracking.
constexpr int32_t kSamplingRateFull = 1;
}  // namespace

namespace profiler {

using proto::AllocationStack;
using proto::BatchAllocationSample;
using proto::EncodedAllocationStack;
using proto::ThreadInfo;

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

MemoryTrackingEnv* MemoryTrackingEnv::Instance(
    JavaVM* vm, const AgentConfig::MemoryConfig& mem_config) {
  if (g_env == nullptr) {
    g_vm = vm;
    // This will attach the current thread to the vm, otherwise
    // CreateJvmtiEnv(g_vm) below will return JNI_EDETACHED error code.
    GetThreadLocalJNI(g_vm);
    // Create a stand-alone jvmtiEnv to avoid any callback conflicts
    // with other profilers' agents.
    jvmtiEnv* jvmti = CreateJvmtiEnv(g_vm);
    g_env = new MemoryTrackingEnv(jvmti, mem_config);
    g_env->Initialize();
  }

  return g_env;
}

MemoryTrackingEnv::MemoryTrackingEnv(
    jvmtiEnv* jvmti, const AgentConfig::MemoryConfig& mem_config)
    : jvmti_(jvmti),
      log_live_alloc_count_(mem_config.use_live_alloc()),
      track_global_jni_refs_(mem_config.track_global_jni_refs()),
      is_first_tracking_(true),
      is_live_tracking_(false),
      app_id_(getpid()),
      class_class_tag_(-1),
      current_capture_time_ns_(-1),
      last_gc_start_ns_(-1),
      max_stack_depth_(mem_config.max_stack_depth()),
      sampling_num_interval_(
          mem_config.sampling_rate().sampling_num_interval()),
      total_alloc_count_(0),
      total_free_count_(0),
      tagged_alloc_count_(0),
      current_class_tag_(kClassStartTag),
      current_object_tag_(kObjectStartTag),
      memory_map_(procfs_, getpid()),
      app_dir_(mem_config.app_dir().empty() ? "/data/app/"
                                            : mem_config.app_dir()) {
  // Locate heap extension functions
  jvmtiError error;
  jvmtiExtensionFunctionInfo* func_info;
  jint func_count = 0;
  error = jvmti_->GetExtensionFunctions(&func_count, &func_info);
  CheckJvmtiError(jvmti, error);

  // Go through all extension functions as we need to deallocate
  for (int i = 0; i < func_count; i++) {
    if (strcmp(kIterateHeapExtFunc, func_info[i].id) == 0) {
      g_iterate_heap_ext_func =
          reinterpret_cast<IterateThroughHeapExt>(func_info[i].func);
    }
    Deallocate(jvmti, func_info[i].id);
    Deallocate(jvmti, func_info[i].short_description);
    for (int j = 0; j < func_info[i].param_count; j++) {
      Deallocate(jvmti, func_info[i].params[j].name);
    }
    Deallocate(jvmti, func_info[i].params);
    Deallocate(jvmti, func_info[i].errors);
  }
  Deallocate(jvmti, func_info);
  assert(g_iterate_heap_ext_func != nullptr);
}

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

  JNIEnv* jni = GetThreadLocalJNI(g_vm);
  // Start AllocWorkerThread - this is alive for the duration of the agent, but
  // it only sends data when a tracking session is ongoing.
  error =
      jvmti_->RunAgentThread(AllocateJavaThread(jvmti_, jni), &AllocDataWorker,
                             this, JVMTI_THREAD_NORM_PRIORITY);
  CheckJvmtiError(jvmti_, error);
  if (log_live_alloc_count_) {
    error = jvmti_->RunAgentThread(AllocateJavaThread(jvmti_, jni),
                                   &AllocCountWorker, this,
                                   JVMTI_THREAD_NORM_PRIORITY);
    CheckJvmtiError(jvmti_, error);
  }
}

static bool startsWith(const std::string& str, const std::string& prefix) {
  if (str.size() < prefix.size()) {
    return false;
  }
  return str.compare(0, prefix.size(), prefix) == 0;
}

void MemoryTrackingEnv::PublishJNIGlobalRefEvent(
    jobject obj, JNIGlobalReferenceEvent::Type type, void* caller_address) {
  jlong obj_tag;
  jvmtiError error = jvmti_->GetTag(obj, &obj_tag);
  if (CheckJvmtiError(jvmti_, error)) {
    return;
  }

  jthread thread = nullptr;
  jvmti_->GetCurrentThread(&thread);
  if (CheckJvmtiError(jvmti_, error)) {
    return;
  }

  JNIGlobalReferenceEvent event;
  event.set_event_type(type);
  event.set_timestamp(clock_.GetCurrentTime());
  event.set_ref_value(reinterpret_cast<int64_t>(obj));
  event.set_object_tag(static_cast<int32_t>(obj_tag));

  bool called_by_app = true;
  {
    // Check the memory map to see if this JNI event
    // comes from App's code or platform.
    Stopwatch stopwatch;
    shared_lock<shared_mutex> lock(mem_map_mutex_);
    const MemoryMap::MemoryRegion* region =
        memory_map_.LookupRegion(reinterpret_cast<uintptr_t>(caller_address));
    if (region != nullptr && !startsWith(region->name, app_dir_)) {
      called_by_app = false;
    }
    g_env->timing_stats_.Track(TimingStats::kMemMapLookup,
                               stopwatch.GetElapsed());
  }

  if (called_by_app) {
    // Obtain backtrace, only if the JNI event comes from the App.
    // Platform call stack is not shown in UI and there is no need to
    // spend time unwinding native stack.
    Stopwatch stopwatch;
    const int kMaxFrames = 30;
    std::vector<std::uintptr_t> addresses = GetBacktrace(kMaxFrames);
    auto event_backtrace = event.mutable_backtrace()->mutable_addresses();
    event_backtrace->Resize(addresses.size(), 0);
    std::copy(addresses.begin(), addresses.end(), event_backtrace->begin());
    g_env->timing_stats_.Track(TimingStats::kNativeBacktrace,
                               stopwatch.GetElapsed());
  }

  JNIEnv* jni = GetThreadLocalJNI(g_vm);
  FillThreadName(jvmti_, jni, thread, event.mutable_thread_name());

  jni_ref_event_queue_.Push(event);
}

void MemoryTrackingEnv::AfterGlobalRefCreated(jobject prototype, jobject gref,
                                              void* caller_address) {
  PublishJNIGlobalRefEvent(gref, JNIGlobalReferenceEvent::CREATE_GLOBAL_REF,
                           caller_address);
}

void MemoryTrackingEnv::BeforeGlobalRefDeleted(jobject gref,
                                               void* caller_address) {
  PublishJNIGlobalRefEvent(gref, JNIGlobalReferenceEvent::DELETE_GLOBAL_REF,
                           caller_address);
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
  std::lock_guard<std::mutex> data_lock(tracking_data_mutex_);
  std::lock_guard<std::mutex> count_lock(tracking_count_mutex_);
  if (is_live_tracking_) {
    return;
  }
  Stopwatch stopwatch;
  is_live_tracking_ = true;
  current_capture_time_ns_ = timestamp;
  total_alloc_count_ = 0;
  total_free_count_ = 0;
  tagged_alloc_count_ = 0;
  current_object_tag_ = kObjectStartTag;

  // Starts an open range of the first allocation sampling mode change.
  profiler::EnqueueAllocationSamplingRateEvent(clock_.GetCurrentTime(),
                                               sampling_num_interval_);

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
  }

  SendBackClassData();

  // Activate tagging of newly allocated objects.
  SetAllocationCallbacksStatus(true);

  if (track_global_jni_refs_) {
    // Set up JNI related callbacks and initiate the memory map.
    SetJNIRefCallbacksStatus(true);
    std::lock_guard<shared_mutex> write_map_lock(mem_map_mutex_);
    memory_map_.Update();
  }

  // Tag and send all objects already allocated on the heap unless they are
  // already tagged.
  IterateThroughHeap();
  Log::V("Tracking initialization took: %lldns",
         (long long)stopwatch.GetElapsed());
}

/**
 * Stops live allocation tracking.
 * - Disable allocation callbacks and clear the queued allocation events.
 * - Class/Method/Stack data are kept around so they can be referenced across
 *   tracking sessions.
 */
void MemoryTrackingEnv::StopLiveTracking(int64_t timestamp) {
  std::lock_guard<std::mutex> data_lock(tracking_data_mutex_);
  std::lock_guard<std::mutex> count_lock(tracking_count_mutex_);
  if (!is_live_tracking_) {
    return;
  }
  is_live_tracking_ = false;
  SetAllocationCallbacksStatus(false);
  if (track_global_jni_refs_) {
    SetJNIRefCallbacksStatus(false);
  }

  allocation_event_queue_.Reset();
  jni_ref_event_queue_.Reset();
  stack_trie_ = Trie<FrameInfo>();

  for (auto method_itr : known_methods_) {
    Deallocate(jvmti_, method_itr.second.table_ptr);
  }
  known_methods_.clear();
  thread_id_map_.clear();
}

/**
 * Updates live allocation sampling rate.
 * Allocation callbacks from this point on will use the new sampling rate
 * to filter allocation events.
 */
void MemoryTrackingEnv::SetSamplingRate(int32_t sampling_num_interval) {
  if (sampling_num_interval == sampling_num_interval_) {
    // No value change, short circuit.
    return;
  }

  std::lock_guard<std::mutex> data_lock(tracking_data_mutex_);
  std::lock_guard<std::mutex> count_lock(tracking_count_mutex_);

  Stopwatch stopwatch;
  sampling_num_interval_ = sampling_num_interval;
  profiler::EnqueueAllocationSamplingRateEvent(clock_.GetCurrentTime(),
                                               sampling_num_interval);

  // If resuming full tracking in an ongoing tracking session, we need to
  // capture a new heap snapshot.
  if (is_live_tracking_ && sampling_num_interval == kSamplingRateFull) {
    current_capture_time_ns_ = clock_.GetCurrentTime();
    total_alloc_count_ = (tagged_alloc_count_ -= total_free_count_);
    total_free_count_ = 0;
    IterateThroughHeap();
  }

  Log::V("Setting sampling rate took: %lldns",
         (long long)stopwatch.GetElapsed());
}

void MemoryTrackingEnv::IterateThroughHeap() {
  jvmtiError error;
  jvmtiHeapCallbacks heap_callbacks;
  memset(&heap_callbacks, 0, sizeof(heap_callbacks));
  heap_callbacks.heap_iteration_callback =
      reinterpret_cast<decltype(heap_callbacks.heap_iteration_callback)>(
          HeapIterationCallback);
  error = g_iterate_heap_ext_func(
      jvmti_, JVMTI_HEAP_FILTER_CLASS_UNTAGGED | JVMTI_HEAP_FILTER_TAGGED,
      nullptr, &heap_callbacks, nullptr);
  CheckJvmtiError(jvmti_, error);
}

/**
 * Send back class data at the beginning of each session. De-duping needs to
 * be done by the caller as class tags remain unique throughout the app.
 * TODO: Only send back new classes since the last tracking session.
 * Note: The Allocation event associated with each class is sent during the
 * initial heap walk.
 */
void MemoryTrackingEnv::SendBackClassData() {
  std::lock_guard<std::mutex> lock(class_data_mutex_);
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

/**
 * Enable/Disable allocation+deallocation callbacks.
 */
void MemoryTrackingEnv::SetAllocationCallbacksStatus(bool enabled) {
  jvmtiEventMode mode = enabled ? JVMTI_ENABLE : JVMTI_DISABLE;
  SetEventNotification(jvmti_, mode, JVMTI_EVENT_VM_OBJECT_ALLOC);
  SetEventNotification(jvmti_, mode, JVMTI_EVENT_OBJECT_FREE);
}

void MemoryTrackingEnv::SetJNIRefCallbacksStatus(bool enabled) {
  GlobalRefListener* ref_listener = enabled ? this : nullptr;
  if (!RegisterJniTableListener(jvmti_, ref_listener)) {
    Log::E("Error while registering new JNI table.");
  }
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
  // class object calls into this method from both places. Or, redefine /
  // retransform classes.
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
  int64_t gc_end_ns = clock_.GetCurrentTime();
  profiler::EnqueueGcStats(last_gc_start_ns_, gc_end_ns);
  timing_stats_.Track(TimingStats::kGc, gc_end_ns - last_gc_start_ns_);

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
  allocation_event_queue_.PrintStats();
  stack_trie_.PrintStats();
  Log::V(">> [MEM AGENT STATS DUMP END]");
#endif
}

void MemoryTrackingEnv::HandleControlSignal(
    const MemoryControlRequest* request) {
  int32_t new_sampling_num_interval;
  switch (request->control_case()) {
    case MemoryControlRequest::kEnableRequest:
      Log::V("Live memory tracking enabled.");
      StartLiveTracking(request->enable_request().timestamp());
      break;
    case MemoryControlRequest::kDisableRequest:
      Log::V("Live memory tracking disabled.");
      StopLiveTracking(request->disable_request().timestamp());
      break;
    case MemoryControlRequest::kSetSamplingRateRequest:
      new_sampling_num_interval = request->set_sampling_rate_request()
                                      .sampling_rate()
                                      .sampling_num_interval();
      Log::V(
          "Live memory tracking sampling rate updated: "
          "sampling_num_interval=%d.",
          new_sampling_num_interval);
      SetSamplingRate(new_sampling_num_interval);
      break;
    case MemoryControlRequest::CONTROL_NOT_SET:
    // Fall through.
    default:
      Log::V("Unknown memory control signal.");
  }
}

jint MemoryTrackingEnv::HeapIterationCallback(jlong class_tag, jlong size,
                                              jlong* tag_ptr, jint length,
                                              void* user_data, jint heap_id) {
  // When we call IterateThroughHeap we filter out untagged classes and tagged
  // objects, so there's no need to check for tag_ptr.
  assert(g_env->class_data_.size() >= class_tag);
  if (class_tag == g_env->class_class_tag_) {
    // Note - we can have remnant Class objects from the ClassLoad phase, which
    // we would't see from GetLoadedClasses and would not be tagged. We don't
    // want to send AllocationEvent for them so simply ignore.
    return JVMTI_VISIT_OBJECTS;
  }

  int32_t tag = g_env->GetNextObjectTag();
  *tag_ptr = tag;

  AllocationEvent event;
  event.set_timestamp(g_env->current_capture_time_ns_);
  AllocationEvent::Allocation* alloc = event.mutable_alloc_data();

  alloc->set_tag(*tag_ptr);
  alloc->set_class_tag(class_tag);
  alloc->set_size(size);
  alloc->set_length(length);
  alloc->set_heap_id(heap_id);

  g_env->allocation_event_queue_.Push(std::move(event));
  g_env->total_alloc_count_++;
  g_env->tagged_alloc_count_++;

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
  g_env->allocation_event_queue_.Push(std::move(klass_event));

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
  alloc_data->set_heap_id(kAppHeapId);
  // Fill thread + stack info.
  FillAllocEventThreadData(g_env, jvmti, jni, thread, alloc_data);
  alloc_event.set_timestamp(g_env->clock_.GetCurrentTime());
  // This can be duplicated as well and de-dup is done on Studio-side.
  g_env->allocation_event_queue_.Push(std::move(alloc_event));
}

void MemoryTrackingEnv::ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                            jthread thread, jobject object,
                                            jclass klass, jlong size) {
  if (!g_env->ShouldSelectSample(g_env->total_alloc_count_++)) {
    return;
  }

  Stopwatch stopwatch;
  {
    g_env->tagged_alloc_count_++;
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

    int32_t tag = g_env->GetNextObjectTag();
    Stopwatch stopwatch_settag;
    error = jvmti->SetTag(object, tag);
    g_env->timing_stats_.Track(TimingStats::kSetTag,
                               stopwatch_settag.GetElapsed());
    CheckJvmtiError(jvmti, error);

    int32_t class_tag = -1;
    {
      std::lock_guard<std::mutex> lock(g_env->class_data_mutex_);
      auto itr = g_env->class_tag_map_.find(klass_info);
      assert(itr != g_env->class_tag_map_.end());
      class_tag = itr->second;
    }

    AllocationEvent event;
    AllocationEvent::Allocation* alloc_data = event.mutable_alloc_data();
    alloc_data->set_tag(tag);
    alloc_data->set_size(size);
    alloc_data->set_class_tag(class_tag);
    alloc_data->set_heap_id(kAppHeapId);
    FillAllocEventThreadData(g_env, jvmti, jni, thread, alloc_data);
    event.set_timestamp(g_env->clock_.GetCurrentTime());
    g_env->allocation_event_queue_.Push(std::move(event));
  }
  g_env->timing_stats_.Track(TimingStats::kAllocate, stopwatch.GetElapsed());
}

void MemoryTrackingEnv::ObjectFreeCallback(jvmtiEnv* jvmti, jlong tag) {
  g_env->total_free_count_++;

  Stopwatch stopwatch;
  {
    AllocationEvent event;
    AllocationEvent::Deallocation* free_data = event.mutable_free_data();
    free_data->set_tag(tag);
    // Associate the free event with the last gc that occurred.
    event.set_timestamp(g_env->last_gc_start_ns_);
    g_env->allocation_event_queue_.Push(std::move(event));
  }
  g_env->timing_stats_.Track(TimingStats::kFree, stopwatch.GetElapsed());
}

void MemoryTrackingEnv::GCStartCallback(jvmtiEnv* jvmti) {
  g_env->LogGcStart();
}

void MemoryTrackingEnv::GCFinishCallback(jvmtiEnv* jvmti) {
  g_env->LogGcFinish();
}

void MemoryTrackingEnv::AllocCountWorker(jvmtiEnv* jvmti, JNIEnv* jni,
                                         void* ptr) {
  Stopwatch stopwatch;
  MemoryTrackingEnv* env = static_cast<MemoryTrackingEnv*>(ptr);
  assert(env != nullptr);
  while (true) {
    int64_t start_time_ns = stopwatch.GetElapsed();
    {
      std::lock_guard<std::mutex> lock(env->tracking_count_mutex_);
      if (env->is_live_tracking_) {
        profiler::EnqueueAllocStats(env->total_alloc_count_,
                                    env->total_free_count_);
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

void MemoryTrackingEnv::AllocDataWorker(jvmtiEnv* jvmti, JNIEnv* jni,
                                        void* ptr) {
  Stopwatch stopwatch;
  MemoryTrackingEnv* env = static_cast<MemoryTrackingEnv*>(ptr);
  assert(env != nullptr);
  while (true) {
    int64_t start_time_ns = stopwatch.GetElapsed();
    env->DrainAllocationEvents(jvmti, jni);
    env->DrainJNIRefEvents(jvmti, jni);

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

int32_t MemoryTrackingEnv::ObtainThreadId(
    const std::string& thread_name, int64_t timestamp,
    google::protobuf::RepeatedPtrField<profiler::proto::ThreadInfo>* threads) {
  // Lookup thread id by name
  auto thread_result = thread_id_map_.emplace(
      std::make_pair(thread_name, thread_id_map_.size() + 1));

  const int32_t thread_id = thread_result.first->second;
  if (thread_result.second) {
    // New thread. Create and send the mapping along the sample.
    proto::ThreadInfo* ti = threads->Add();
    ti->set_thread_id(thread_id);
    ti->set_thread_name(thread_name);
    ti->set_timestamp(timestamp);
  }

  return thread_id;
}

// Drain allocation_event_queue_ and send events to perfd
void MemoryTrackingEnv::DrainAllocationEvents(jvmtiEnv* jvmti, JNIEnv* jni) {
  std::lock_guard<std::mutex> lock(tracking_data_mutex_);
  if (!is_live_tracking_) {
    return;
  }

  BatchAllocationSample sample;
  // Gather all the data currently in the queue and push to perfd.
  // TODO: investigate whether we need to set time cap for large queue.
  std::deque<AllocationEvent> queued_data = allocation_event_queue_.Drain();
  sample.mutable_events()->Reserve(
      std::min(queued_data.size(), static_cast<size_t>(kDataBatchSize)));
  while (!queued_data.empty()) {
    AllocationEvent* event = sample.add_events();
    event->CopyFrom(queued_data.front());
    queued_data.pop_front();

    switch (event->event_case()) {
      case AllocationEvent::kAllocData: {
        AllocationEvent::Allocation* alloc_data = event->mutable_alloc_data();
        int stack_size = alloc_data->method_ids_size();
        assert(stack_size == alloc_data->location_ids_size());

        // Switch to storing the thread id in the allocation event.
        auto thread_id =
            ObtainThreadId(alloc_data->thread_name(), event->timestamp(),
                           sample.mutable_thread_infos());
        alloc_data->set_thread_id(thread_id);
        alloc_data->clear_thread_name();

        // Store and encode the stack into trie.
        // TODO - consider moving trie storage to perfd?
        if (stack_size > 0) {
          std::vector<FrameInfo> reversed_stack(stack_size);
          for (int i = 0; i < stack_size; i++) {
            int64_t method = alloc_data->method_ids(i);
            int64_t location = alloc_data->location_ids(i);
            reversed_stack[stack_size - i - 1] = {method, location};
            if (known_methods_.find(method) == known_methods_.end()) {
              // New method. Query method name ane line number info.
              CacheMethodInfo(this, jvmti, jni, sample, method);
            }
          }

          auto stack_result = stack_trie_.insert(reversed_stack);
          if (stack_result.second) {
            // New stack. Append the stack info into BatchAllocationSample
            EncodedAllocationStack* encoded_stack = sample.add_stacks();
            encoded_stack->set_timestamp(event->timestamp());
            encoded_stack->set_stack_id(stack_result.first);
            // Yet reverse again so first entry is top of stack.
            for (int j = stack_size - 1; j >= 0; j--) {
              int32_t line_number = kInvalidLineNumber;
              auto itr = known_methods_.find(reversed_stack[j].method_id);
              if (reversed_stack[j].location_id != -1 &&
                  itr->second.entry_count > 0) {
                line_number = FindLineNumber(reversed_stack[j].location_id,
                                             itr->second.entry_count,
                                             itr->second.table_ptr);
              }
              encoded_stack->add_method_ids(reversed_stack[j].method_id);
              encoded_stack->add_line_numbers(line_number);
            }
          }
          // Only store the leaf index into alloc_data.
          // The full stack will be looked up from EncodedStack on
          // studio-side.
          alloc_data->clear_method_ids();
          alloc_data->clear_location_ids();
          alloc_data->set_stack_id(stack_result.first);
        }
      } break;
      default:
        // Do nothing for Klass + Deallocation.
        break;
    }

    if (sample.events_size() >= kDataBatchSize) {
      profiler::EnqueueAllocationEvents(sample);
      sample.Clear();
    }
  }

  if (sample.events_size() > 0) {
    profiler::EnqueueAllocationEvents(sample);
  }
}

void MemoryTrackingEnv::FillJniEventsModuleMap(BatchJNIGlobalRefEvent* batch) {
  bool memory_map_is_updated = false;
  std::unordered_set<uintptr_t> reported_regions;
  MemoryMap::MemoryRegion last_seen_region{"", 0, 0, 0};
  for (auto& event : batch->events()) {
    for (uint64_t address : event.backtrace().addresses()) {
      auto addr = static_cast<uintptr_t>(address);
      if (last_seen_region.contains(addr)) {
        // This address belongs to the region we just added to the proto
        // memory map, no need to go any further.
        continue;
      }

      // Lookup address in the memory map under shared read lock.
      MemoryMap::MemoryRegion region{"", 0, 0, 0};
      {
        shared_lock<shared_mutex> read_lock(mem_map_mutex_);
        const MemoryMap::MemoryRegion* region_ptr =
            memory_map_.LookupRegion(addr);
        if (region_ptr != nullptr) {
          region = *region_ptr;
        }
      }

      if (region.name.empty()) {
        // If the address is not found in the memory map, try to refresh it,
        // because new module might be loaded, but don't do it more than
        // once per batch.
        if (!memory_map_is_updated) {
          // Take an exclusive lock and update the map.
          std::lock_guard<shared_mutex> write_lock(mem_map_mutex_);
          if (!memory_map_.Update()) {
            // Reading memory map has failed. Report it and keep going,
            // because the old map is still intact and can still be used.
            Log::E("Failed reading memory map from: /proc/%d/maps", getpid());
          }
          memory_map_is_updated = true;
        }
        shared_lock<shared_mutex> read_lock(mem_map_mutex_);
        const MemoryMap::MemoryRegion* region_ptr =
            memory_map_.LookupRegion(addr);
        if (region_ptr != nullptr) {
          region = *region_ptr;
        }
      }

      if (!region.name.empty()) {
        if (reported_regions.insert(region.start_address).second) {
          // This region hasn't been reported before, we need to add it
          // to the region map in the batch.
          auto proto_region = batch->mutable_memory_map()->add_regions();
          proto_region->set_name(region.name);
          proto_region->set_start_address(region.start_address);
          proto_region->set_end_address(region.end_address);
          proto_region->set_file_offset(region.file_offset);
        }
        last_seen_region = region;
      }
    }
  }
}

void MemoryTrackingEnv::DrainJNIRefEvents(jvmtiEnv* jvmti, JNIEnv* jni) {
  std::lock_guard<std::mutex> lock(tracking_data_mutex_);
  if (!is_live_tracking_) {
    return;
  }

  BatchJNIGlobalRefEvent batch;
  auto queued_data(jni_ref_event_queue_.Drain());
  batch.mutable_events()->Reserve(
      std::min(queued_data.size(), static_cast<size_t>(kDataBatchSize)));
  while (!queued_data.empty()) {
    JNIGlobalReferenceEvent* event = batch.add_events();
    event->CopyFrom(queued_data.front());
    queued_data.pop_front();

    // Switch to storing the thread id in the JNI event.
    auto thread_id = ObtainThreadId(event->thread_name(), event->timestamp(),
                                    batch.mutable_thread_infos());
    event->set_thread_id(thread_id);
    event->clear_thread_name();

    if (batch.events_size() >= kDataBatchSize) {
      FillJniEventsModuleMap(&batch);
      profiler::EnqueueJNIGlobalRefEvents(batch);
      batch.Clear();
    }
  }

  if (batch.events_size() > 0) {
    FillJniEventsModuleMap(&batch);
    profiler::EnqueueJNIGlobalRefEvents(batch);
  }
}

void MemoryTrackingEnv::CacheMethodInfo(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                                        JNIEnv* jni,
                                        BatchAllocationSample& sample,
                                        int64_t method_id) {
  jvmtiError error;
  Stopwatch stopwatch;
  {
    jmethodID id = reinterpret_cast<jmethodID>(method_id);

    char* method_name;
    error = jvmti->GetMethodName(id, &method_name, nullptr, nullptr);
    CheckJvmtiError(jvmti, error);

    jclass klass;
    error = jvmti->GetMethodDeclaringClass(id, &klass);
    CheckJvmtiError(jvmti, error);
    assert(klass != nullptr);

    ScopedLocalRef<jclass> scoped_klass(jni, klass);
    char* klass_name;
    error = jvmti->GetClassSignature(scoped_klass.get(), &klass_name, nullptr);

    AllocationStack::StackFrame* method = sample.add_methods();
    method->set_method_id(method_id);
    method->set_method_name(method_name);
    method->set_class_name(klass_name);

    Deallocate(jvmti, method_name);
    Deallocate(jvmti, klass_name);

    jint entry_count = 0;
    jvmtiLineNumberEntry* line_number_entry = nullptr;
    jvmti->GetLineNumberTable(id, &entry_count, &line_number_entry);
    env->known_methods_.emplace(std::make_pair(
        method_id, LineNumberInfo{entry_count, line_number_entry}));
  }
  env->timing_stats_.Track(TimingStats::kResolveCallstack,
                           stopwatch.GetElapsed());
}

int32_t MemoryTrackingEnv::FindLineNumber(int64_t location_id, int entry_count,
                                          jvmtiLineNumberEntry* table_ptr) {
  int32_t line_number = kInvalidLineNumber;
  for (int i = 0; i < entry_count; i++) {
    jvmtiLineNumberEntry entry = table_ptr[i];
    if (entry.start_location > location_id) {
      break;
    }
    line_number = entry.line_number;
  }

  return line_number;
}

void MemoryTrackingEnv::FillAllocEventThreadData(
    MemoryTrackingEnv* env, jvmtiEnv* jvmti, JNIEnv* jni, jthread thread,
    AllocationEvent::Allocation* alloc_data) {
  env->FillThreadName(jvmti, jni, thread, alloc_data->mutable_thread_name());

  // Collect stack frames
  int32_t depth = env->max_stack_depth_;
  jvmtiFrameInfo frames[depth];
  jint count = 0;
  Stopwatch stopwatch;
  jvmtiError error = jvmti->GetStackTrace(thread, 0, depth, frames, &count);
  env->timing_stats_.Track(TimingStats::kGetCallstack, stopwatch.GetElapsed());
  CheckJvmtiError(jvmti, error);
  for (int i = 0; i < count; i++) {
    int64_t method_id = reinterpret_cast<int64_t>(frames[i].method);
    // jlocation is just a jlong.
    int64_t location_id = reinterpret_cast<jlong>(frames[i].location);
    alloc_data->add_method_ids(method_id);
    alloc_data->add_location_ids(location_id);
  }
}

void MemoryTrackingEnv::FillThreadName(jvmtiEnv* jvmti, JNIEnv* jni,
                                       jthread thread,
                                       std::string* thread_name) {
  assert(thread_name != nullptr);
  jvmtiThreadInfo ti;
  Stopwatch stopwatch;
  jvmtiError error = jvmti->GetThreadInfo(thread, &ti);
  timing_stats_.Track(TimingStats::kThreadInfo, stopwatch.GetElapsed());
  if (CheckJvmtiError(jvmti, error)) {
    return;
  }
  ScopedLocalRef<jobject> scoped_thread_group(jni, ti.thread_group);
  ScopedLocalRef<jobject> scoped_class_loader(jni, ti.context_class_loader);
  *thread_name = ti.name;
  Deallocate(jvmti, ti.name);
}

void MemoryTrackingEnv::GetClassInfo(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                                     JNIEnv* jni, jclass klass,
                                     ClassInfo* klass_info) {
  jvmtiError error;

  // Get class loader id.
  Stopwatch stopwatch;
  klass_info->class_loader_id = GetClassLoaderId(jvmti, jni, klass);
  env->timing_stats_.Track(TimingStats::kClassLoader, stopwatch.GetElapsed());

  // Get class name.
  char* sig_mutf8;
  stopwatch.Start();
  error = jvmti->GetClassSignature(klass, &sig_mutf8, nullptr);
  env->timing_stats_.Track(TimingStats::kClassName, stopwatch.GetElapsed());
  CheckJvmtiError(jvmti, error);

  // TODO this is wrong. We need to parse mutf-8.
  klass_info->class_name = sig_mutf8;
  Deallocate(jvmti, sig_mutf8);
}
}  // namespace profiler
