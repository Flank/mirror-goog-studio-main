/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef MEMORY_TRACKING_ENV_H
#define MEMORY_TRACKING_ENV_H

#include <unistd.h>

#include <atomic>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "jni_function_table.h"
#include "jvmti.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/internal_memory.grpc.pb.h"
#include "proto/memory.grpc.pb.h"
#include "proto/transport.grpc.pb.h"
#include "stats.h"
#include "utils/clock.h"
#include "utils/log.h"
#include "utils/memory_map.h"
#include "utils/procfs_files.h"
#include "utils/producer_consumer_queue.h"
#include "utils/shared_mutex.h"
#include "utils/trie.h"

using profiler::Clock;
using profiler::proto::AgentConfig;
using profiler::proto::AllocatedClass;
using profiler::proto::AllocationEvent;
using profiler::proto::BatchAllocationContexts;
using profiler::proto::BatchAllocationEvents;
using profiler::proto::BatchJNIGlobalRefEvent;
using profiler::proto::JNIGlobalReferenceEvent;
using profiler::proto::MemoryControlRequest;

namespace profiler {

// Data structure for a loaded class
struct ClassInfo {
  std::string class_name;
  int32_t class_loader_id;
  bool operator==(const ClassInfo& other) const {
    return class_name.compare(other.class_name) == 0 &&
           class_loader_id == other.class_loader_id;
  }
};

struct ClassInfoHash {
  std::size_t operator()(const ClassInfo& key) const {
    return std::hash<std::string>()(key.class_name) ^ key.class_loader_id;
  }
};

// Struct of a line number table associated with a jmethodID.
// This maps the execution position pointer to Java method line number.
struct LineNumberInfo {
  jint entry_count;
  jvmtiLineNumberEntry* table_ptr;
};

#ifndef NDEBUG
using ClassTagMap =
    tracking::unordered_map<ClassInfo, int32_t, kClassTagMap, ClassInfoHash>;
using ClassGlobalRefs = tracking::vector<jobject, kClassGlobalRefs>;
using ClassData = tracking::vector<AllocatedClass, kClassData>;
using MethodIdMap =
    tracking::unordered_map<int64_t, LineNumberInfo, kMethodIds>;
using ThreadIdMap = tracking::unordered_map<std::string, int32_t, kThreadIdMap>;
#else
using ClassTagMap = std::unordered_map<ClassInfo, int32_t, ClassInfoHash>;
using ClassGlobalRefs = std::vector<jobject>;
using ClassData = std::vector<AllocatedClass>;
using MethodIdMap = std::unordered_map<int64_t, LineNumberInfo>;
using ThreadIdMap = std::unordered_map<std::string, int32_t>;
#endif

class MemoryTrackingEnv : public GlobalRefListener {
 public:
  static MemoryTrackingEnv* Instance(
      JavaVM* vm, const AgentConfig::MemoryConfig& mem_config);

  void AfterGlobalRefCreated(jobject prototype, jobject gref,
                             void* caller_address) override;
  void BeforeGlobalRefDeleted(jobject gref, void* caller_address) override;

  void SetSamplingRate(int32_t sampling_num_interval);
  void HandleStartAllocTracking(const proto::Command& command);
  void HandleStopAllocTracking(const proto::Command& command);

 private:
  // POD for encoding the method/instruction location data into trie.
  struct FrameInfo {
    int64_t method_id, location_id;
    inline bool operator==(const FrameInfo& other) const {
      return method_id == other.method_id && location_id == other.location_id;
    }
  };

  explicit MemoryTrackingEnv(jvmtiEnv* jvmti,
                             const AgentConfig::MemoryConfig& mem_config);

  // Environment is alive through the app's lifetime, don't bother cleaning up.
  ~MemoryTrackingEnv() = delete;
  MemoryTrackingEnv(const MemoryTrackingEnv&) = delete;
  MemoryTrackingEnv& operator=(const MemoryTrackingEnv&) = delete;

  void Initialize();
  bool StartLiveTracking(int64_t timestamp);
  bool StopLiveTracking(int64_t timestamp);
  const AllocatedClass& RegisterNewClass(jvmtiEnv* jvmti, JNIEnv* jni,
                                         jclass klass);
  void SendBackClassData();
  void SetAllocationCallbacksStatus(bool enabled);
  void SetJNIRefCallbacksStatus(bool enabled);
  void LogGcStart();
  void LogGcFinish();
  void IterateThroughHeap();

  inline int32_t GetNextClassTag() { return current_class_tag_++; }
  inline int32_t GetNextObjectTag() { return current_object_tag_++; }
  inline bool ShouldSelectSample(int32_t sampling_num) {
    return sampling_num_interval_ > 0 &&
           sampling_num % sampling_num_interval_ == 0;
  }

  void HandleControlSignal(const MemoryControlRequest* request);

  void PublishJNIGlobalRefEvent(jobject obj, JNIGlobalReferenceEvent::Type type,
                                void* caller_address);

  // An heap walker used for setting up an initial snapshot of live objects.
  static jint JNICALL HeapIterationCallback(jlong class_tag, jlong size,
                                            jlong* tag_ptr, jint length,
                                            void* user_data, jint heap_id);

  // JVMTI Callback for when a class object is ready.
  static void JNICALL ClassPrepareCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                           jthread thread, jclass klass);
  // JVMTI Callback for object allocation events
  static void JNICALL ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                          jthread thread, jobject object,
                                          jclass object_klass, jlong size);
  // JVMTI Callback for object free events.
  static void JNICALL ObjectFreeCallback(jvmtiEnv* jvmti, jlong tag);
  // JVMTI Callback for garbage collection start events.
  static void JNICALL GCStartCallback(jvmtiEnv* jvmti);
  // JVMTI Callback for garbage collection end events.
  static void JNICALL GCFinishCallback(jvmtiEnv* jvmti);

  // Thread to send allocation data to perfd.
  static void JNICALL AllocDataWorker(jvmtiEnv* jvmti, JNIEnv* jni, void* arg);

  // Sends the input list of allocation events to perfd
  void SendAllocationEvents(jvmtiEnv* jvmti, JNIEnv* jni,
                            std::deque<AllocationEvent>& queue);

  // Sends the input list of jni ref events to perfd
  void SendJNIRefEvents(jvmtiEnv* jvmti, JNIEnv* jni,
                        std::deque<JNIGlobalReferenceEvent>& queue);

  // Thread to send allocation count data to perfd.
  static void JNICALL AllocCountWorker(jvmtiEnv* jvmti, JNIEnv* jni, void* arg);

  // Helper method for retrieving methods names and line numbers corresponding
  // to |method_id| and cache them into |contexts| and our MethodIdMap
  static void CacheMethodInfo(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                              JNIEnv* jni, BatchAllocationContexts& contexts,
                              int64_t method_id);

  // Helper method for retrieivng line number.
  static int32_t FindLineNumber(int64_t location_id, int entry_count,
                                jvmtiLineNumberEntry* table_ptr);

  // Helper method for gathering thread-related info (e.g. name, callstack)
  // and populate them into |alloc_data|.
  static void FillAllocEventThreadData(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                                       JNIEnv* jni, jthread thead,
                                       AllocationEvent::Allocation* alloc_data);

  // Populates thread name associated with a given JNI thead handle;
  void FillThreadName(jvmtiEnv* jvmti, JNIEnv* jni, jthread thead,
                      std::string* thread_name);

  // Lookup a thread id by a thread name and create a new enty if needed.
  int32_t ObtainThreadId(
      const std::string& thread_name,
      google::protobuf::RepeatedPtrField<proto::ThreadInfo>* threads);

  void FillJniEventsModuleMap(BatchJNIGlobalRefEvent* batch,
                              proto::MemoryMap* memory_map);

  // For a particular class object, populate |klass_info| with the corresponding
  // values.
  static void GetClassInfo(MemoryTrackingEnv* env, jvmtiEnv* jvmti, JNIEnv* jni,
                           jclass klass, ClassInfo* klass_info);

  SteadyClock clock_;
  TimingStats timing_stats_;

  jvmtiEnv* jvmti_;
  bool log_live_alloc_count_;
  bool track_global_jni_refs_;
  bool is_first_tracking_;
  bool is_live_tracking_;
  int32_t app_id_;
  int32_t class_class_tag_;
  // The time when allocation tracking was last turned on (regardless of
  // sampling mode).
  int64_t last_tracking_start_time_ns_;
  // The time when the sampling mode was last set to full. (e..g this is the
  // time where the entire heap was last visited)
  int64_t current_capture_time_ns_;
  int64_t last_gc_start_ns_;
  int32_t max_stack_depth_;
  int32_t sampling_num_interval_;
  std::mutex tracking_data_mutex_;
  std::mutex tracking_count_mutex_;
  std::atomic<int32_t> total_alloc_count_;

  // We only get free events for tagged objects so in sampled mode this will be
  // inaccurate.
  std::atomic<int32_t> total_free_count_;

  // Keep track of tagged allocation count so we don't have to visit tagged
  // objects again during subsequent heap walks for sampling mode change.
  std::atomic<int32_t> tagged_alloc_count_;

  std::atomic<int32_t> current_class_tag_;
  std::atomic<int32_t> current_object_tag_;

  std::mutex class_data_mutex_;
  ProducerConsumerQueue<AllocationEvent> allocation_event_queue_;
  ProducerConsumerQueue<JNIGlobalReferenceEvent> jni_ref_event_queue_;
  Trie<FrameInfo> stack_trie_;
  ClassTagMap class_tag_map_;
  ClassGlobalRefs class_global_refs_;
  ClassData class_data_;
  MethodIdMap known_methods_;
  ThreadIdMap thread_id_map_;
  ProcfsFiles procfs_;
  shared_mutex mem_map_mutex_;
  MemoryMap memory_map_;
  const std::string app_dir_;
};

}  // namespace profiler

#endif  // MEMORY_TRACKING_ENV_H
