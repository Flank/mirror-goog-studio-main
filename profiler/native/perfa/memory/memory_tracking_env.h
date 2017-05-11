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
using profiler::proto::BatchAllocationSample;
using profiler::proto::MemoryControlRequest;
using profiler::proto::AllocationEvent;

namespace profiler {

#ifndef NDEBUG
using ClassTagMap = tracking::unordered_map<std::string, long, kClassTagMap>;
using ClassGlobalRefs = tracking::vector<jobject, kClassGlobalRefs>;
using ClassData = tracking::vector<AllocationEvent::Klass, kClassData>;
using MethodIdSet = tracking::unordered_set<long, kMethodIds>;
#else
using ClassTagMap = std::unordered_map<std::string, long>;
using ClassGlobalRefs = std::vector<jobject>;
using ClassData = std::vector<AllocationEvent::Klass>;
using MethodIdSet = std::unordered_set<long>;
#endif

class MemoryTrackingEnv {
 public:
  static MemoryTrackingEnv* Instance(JavaVM* vm);

 private:
  explicit MemoryTrackingEnv(jvmtiEnv* jvmti);

  // Environment is alive through the app's lifetime, don't bother cleaning up.
  ~MemoryTrackingEnv() = delete;
  MemoryTrackingEnv(const MemoryTrackingEnv&) = delete;
  MemoryTrackingEnv& operator=(const MemoryTrackingEnv&) = delete;

  void Initialize();
  void StartLiveTracking(int64_t timestamp);
  void StopLiveTracking(int64_t timestamp);
  void RegisterNewClass(JNIEnv* jni, jclass klass, AllocationEvent* event);
  void LogGcStart();
  void LogGcFinish();

  long GetNextClassTag() { return current_class_tag_++; }
  long GetNextObjectTag() { return current_object_tag_++; }

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

  // Helper methods for retrieving methods names corresponding to the method_ids
  // and inserting them into the BatchAllocationSample.
  static void SetSampleMethods(MemoryTrackingEnv* env, jvmtiEnv* jvmti,
                               JNIEnv* jni, BatchAllocationSample& sample,
                               const std::vector<long>& method_ids);

  SteadyClock clock_;
  TimingStats timing_stats_;

  jvmtiEnv* jvmti_;
  bool is_live_tracking_;
  int32_t app_id_;
  int64_t current_capture_time_ns_;
  int64_t last_gc_start_ns_;
  std::atomic<int> total_live_count_;
  std::atomic<int> total_free_count_;
  std::atomic<long> current_class_tag_;
  std::atomic<long> current_object_tag_;

  std::mutex class_data_mutex_;
  ProducerConsumerQueue<AllocationEvent> event_queue_;
  Trie<long> stack_trie_;
  ClassTagMap class_tag_map_;
  ClassGlobalRefs class_global_refs_;
  ClassData class_data_;
  MethodIdSet known_method_ids_;
};

}  // namespace profiler

#endif  // MEMORY_TRACKING_ENV_H
