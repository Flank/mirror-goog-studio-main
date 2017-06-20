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

#include "jvmti.h"

#include <unistd.h>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "proto/internal_memory.grpc.pb.h"
#include "proto/memory.grpc.pb.h"
#include "stats.h"
#include "utils/clock.h"
#include "utils/log.h"
#include "utils/producer_consumer_queue.h"
#include "utils/trie.h"

using profiler::Clock;
using profiler::proto::AllocatedClass;
using profiler::proto::AllocationEvent;
using profiler::proto::BatchAllocationSample;
using profiler::proto::MemoryControlRequest;

namespace profiler {

#ifndef NDEBUG
using ClassTagMap = tracking::unordered_map<std::string, int32_t, kClassTagMap>;
using ClassGlobalRefs = tracking::vector<jobject, kClassGlobalRefs>;
using ClassData = tracking::vector<AllocatedClass, kClassData>;
using MethodIdSet = tracking::unordered_set<int64_t, kMethodIds>;
#else
using ClassTagMap = std::unordered_map<std::string, int32_t>;
using ClassGlobalRefs = std::vector<jobject>;
using ClassData = std::vector<AllocatedClass>;
using MethodIdSet = std::unordered_set<int64_t>;
#endif

class MemoryTrackingEnv {
 public:
  // POD for encoding the method/instruction location data into trie.
  struct FrameInfo {
    int64_t method_id, location_id;
    inline bool operator==(const FrameInfo& other) const {
      return method_id == other.method_id && location_id == other.location_id;
    }
  };

  static MemoryTrackingEnv* Instance(JavaVM* vm, bool log_live_alloc_count);

 private:
  explicit MemoryTrackingEnv(jvmtiEnv* jvmti, bool log_live_alloc_count);

  // Environment is alive through the app's lifetime, don't bother cleaning up.
  ~MemoryTrackingEnv() = delete;
  MemoryTrackingEnv(const MemoryTrackingEnv&) = delete;
  MemoryTrackingEnv& operator=(const MemoryTrackingEnv&) = delete;

  void Initialize();
  void StartLiveTracking(int64_t timestamp);
  void StopLiveTracking(int64_t timestamp);
  void RegisterNewClass(JNIEnv* jni, jclass klass);
  void LogGcStart();
  void LogGcFinish();

  inline int32_t GetNextClassTag() { return current_class_tag_++; }
  inline int32_t GetNextObjectTag() { return current_object_tag_++; }

  void HandleControlSignal(const MemoryControlRequest* request);

  // An heap walker used for setting up an initial snapshot of live objects.
  static jint JNICALL HeapIterationCallback(jlong class_tag, jlong size,
                                            jlong* tag_ptr, jint length,
                                            void* user_data);

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

  // Helper method for retrieving methods names corresponding to the method_ids
  // and inserting them into the BatchAllocationSample.
  static void SetSampleMethods(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                               JNIEnv* jni, BatchAllocationSample& sample,
                               const std::vector<int64_t>& method_ids);

  // Helper method for retrieivng line number.
  static int32_t FindLineNumber(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                                int64_t method_id, int64_t location_id);

  SteadyClock clock_;
  TimingStats timing_stats_;

  jvmtiEnv* jvmti_;
  bool log_live_alloc_count_;
  bool is_first_tracking_;
  bool is_live_tracking_;
  int32_t app_id_;
  int64_t current_capture_time_ns_;
  int64_t last_gc_start_ns_;
  std::mutex tracking_mutex_;
  std::atomic<int32_t> total_live_count_;
  std::atomic<int32_t> total_free_count_;
  std::atomic<int32_t> current_class_tag_;
  std::atomic<int32_t> current_object_tag_;

  std::mutex class_data_mutex_;
  ProducerConsumerQueue<AllocationEvent> event_queue_;
  Trie<FrameInfo> stack_trie_;
  ClassTagMap class_tag_map_;
  ClassGlobalRefs class_global_refs_;
  ClassData class_data_;
  MethodIdSet known_method_ids_;
};

}  // namespace profiler

#endif  // MEMORY_TRACKING_ENV_H
