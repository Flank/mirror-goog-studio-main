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
#include "memory_agent.h"

#include <cassert>
#include <cstring>

#include "agent/agent.h"
#include "agent/support/memory_stats_logger.h"
#include "perfa/jvmti_helper.h"
#include "perfa/scoped_local_ref.h"
#include "utils/log.h"
#include "utils/stopwatch.h"

namespace {

static JavaVM* vm_;
static profiler::MemoryAgent* agent_;

// Start tag of Class objects - use 1 as 0 represents no tag.
constexpr long kClassStartTag = 1;
// Start tag of all other instance objects
// This assume enough buffer for the number of classes that are in an
// application.
constexpr long kObjectStartTag = 1e6;

const char* kClassClass = "Ljava/lang/Class;";
}

namespace profiler {

using proto::RecordAllocationEventsRequest;

MemoryAgent* MemoryAgent::Instance(JavaVM* vm) {
  if (agent_ == nullptr) {
    // Create a stand-alone jvmtiEnv to avoid any callback conflicts
    // with other profilers' agents.
    vm_ = vm;
    jvmtiEnv* jvmti = CreateJvmtiEnv(vm_);
    agent_ = new MemoryAgent(jvmti);
    agent_->Initialize();
  }

  return agent_;
}

MemoryAgent::MemoryAgent(jvmtiEnv* jvmti)
    : jvmti_(jvmti),
      is_live_tracking_(false),
      app_id_(getpid()),
      last_tracking_start_ns_(-1),
      last_gc_start_ns_(-1),
      current_class_tag_(kClassStartTag),
      current_object_tag_(kObjectStartTag) {}

void MemoryAgent::Initialize() {
  jvmtiError error;

  SetAllCapabilities(jvmti_);

  // Hook up event callbacks
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.GarbageCollectionStart = &GCStartCallback;
  callbacks.GarbageCollectionFinish = &GCFinishCallback;
  error = jvmti_->SetEventCallbacks(&callbacks, sizeof(callbacks));
  CheckJvmtiError(jvmti_, error);

  // Enable events
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_START);
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_FINISH);

  auto memory_component = Agent::Instance().memory_component();
  memory_component->RegisterMemoryControlHandler(std::bind(
      &MemoryAgent::HandleControlSignal, this, std::placeholders::_1));
  memory_component->OpenControlStream();
}

/**
 * Starts live allocation tracking. The initialization process involves:
 * - Hooks on requried callbacks for alloc tracking
 * - Tagging all classes that are already loaded
 * - Walk through the heap to tag all existing objects
 * - Sets up a agent thread which offload data back to perfd/studio.
 */
void MemoryAgent::StartLiveTracking() {
  if (is_live_tracking_) {
    return;
  }
  is_live_tracking_ = true;
  last_tracking_start_ns_ = clock_.GetCurrentTime();

  // Called from grpc so we need to attach.
  JNIEnv* jni = GetThreadLocalJNI(vm_);
  jvmtiError error;

  // Trigger a GC - this is necessary to clean up any Class objects
  // that are still left behind from the ClassLoad stage, which
  // we would not get from the GetLoadedClasses below, and we don't
  // care about them being on the heap.
  error = jvmti_->ForceGarbageCollection();
  CheckJvmtiError(jvmti_, error);

  // Hook up the event callbacks
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  // Note: we only track ClassPrepare as class information like
  // fields and methods are not yet available during ClassLoad.
  callbacks.ClassPrepare = &ClassPrepareCallback;
  callbacks.VMObjectAlloc = &ObjectAllocCallback;
  callbacks.ObjectFree = &ObjectFreeCallback;
  error = jvmti_->SetEventCallbacks(&callbacks, sizeof(callbacks));
  CheckJvmtiError(jvmti_, error);
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE);

  // Tag all loaded classes and send to perfd.
  jint class_count = 0;
  jclass* classes;
  error = jvmti_->GetLoadedClasses(&class_count, &classes);
  CheckJvmtiError(jvmti_, error);
  RecordAllocationEventsRequest class_request;
  for (int i = 0; i < class_count; ++i) {
    ScopedLocalRef<jclass> klass(jni, classes[i]);

    AllocationEvent* event = class_request.add_events();
    RegisterNewClass(jni, klass.get(), event);
    event->set_tracking_start_time(last_tracking_start_ns_);
    event->set_timestamp(last_tracking_start_ns_);
  }
  class_request.set_timestamp(last_tracking_start_ns_);
  class_request.set_process_id(app_id_);
  profiler::EnqueueAllocationEvents(class_request);
  Deallocate(jvmti_, classes);

  // Tag all objects already allocated on the heap.
  RecordAllocationEventsRequest snapshot_request;
  snapshot_request.set_timestamp(last_tracking_start_ns_);
  snapshot_request.set_process_id(app_id_);
  jvmtiHeapCallbacks heap_callbacks;
  memset(&heap_callbacks, 0, sizeof(heap_callbacks));
  heap_callbacks.heap_iteration_callback = &HeapIterationCallback;
  error = jvmti_->IterateThroughHeap(0, nullptr, &heap_callbacks,
                                     &snapshot_request);
  CheckJvmtiError(jvmti_, error);
  profiler::EnqueueAllocationEvents(snapshot_request);

  // Enable allocation+deallocation callbacks after initial heap walk.
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_VM_OBJECT_ALLOC);
  SetEventNotification(jvmti_, JVMTI_ENABLE, JVMTI_EVENT_OBJECT_FREE);
}

/**
 * TODO: Stops live allocation tracking by disabling the event notification
 * and removing all global refs that we have created for jclasses.
 */
void MemoryAgent::StopLiveTracking() {}

void MemoryAgent::RegisterNewClass(JNIEnv* jni, jclass klass,
                                   AllocationEvent* event) {
  std::lock_guard<std::mutex> lock(class_data_mutex_);

  jvmtiError error;

  char* sig_mutf8;
  error = jvmti_->GetClassSignature(klass, &sig_mutf8, nullptr);
  CheckJvmtiError(jvmti_, error);
  // TODO this is wrong. We need to parse mutf-8.
  std::string klass_name = sig_mutf8;
  Deallocate(jvmti_, sig_mutf8);

  // TODO: possible scenario where same class gets loaded from
  // different loaders?
  assert(agent_->class_tag_map_.find(klass_name) ==
         agent_->class_tag_map_.end());

  long tag = GetNextClassTag();
  error = jvmti_->SetTag(klass, tag);
  CheckJvmtiError(jvmti_, error);

  AllocationEvent::Klass klass_data;
  klass_data.set_tag(tag);
  klass_data.set_name(klass_name);
  class_data_.push_back(klass_data);
  class_tag_map_.emplace(std::make_pair(klass_name, tag));
  assert(class_data_.size() == tag);

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

void MemoryAgent::LogGcStart() { last_gc_start_ns_ = clock_.GetCurrentTime(); }

void MemoryAgent::LogGcFinish() {
  profiler::EnqueueGcStats(last_gc_start_ns_, clock_.GetCurrentTime());
}

void MemoryAgent::HandleControlSignal(const MemoryControlRequest* request) {
  switch (request->signal()) {
    case MemoryControlRequest::ENABLE_TRACKING:
      Log::V("Live memory tracking enabled.");
      StartLiveTracking();
      break;
    case MemoryControlRequest::DISABLE_TRACKING:
      Log::V("Live memory tracking disabled.");
      StopLiveTracking();
      break;
    default:
      Log::V("Unknown memory control signal.");
  }
}

jint MemoryAgent::HeapIterationCallback(jlong class_tag, jlong size,
                                        jlong* tag_ptr, jint length,
                                        void* user_data) {
  RecordAllocationEventsRequest* request =
      (RecordAllocationEventsRequest*)user_data;

  assert(user_data != nullptr);
  assert(class_tag != 0);  // All classes should be tagged by this point.
  assert(agent_->class_data_.size() >= class_tag);

  // Class tag starts at 1, thus the offset in its vector position.
  const auto class_data = agent_->class_data_[class_tag - 1];
  if (class_data.name().compare(kClassClass) == 0) {
    // Skip Class objects as they should already be tagged.
    // TODO account for their sizes in Ljava/lang/Class;
    // Alternatively, perform the bookkeeping on Studio side.
    assert(*tag_ptr != 0);
    return JVMTI_VISIT_OBJECTS;
  }

  long tag = agent_->GetNextObjectTag();
  *tag_ptr = tag;

  AllocationEvent* event = request->add_events();
  event->set_tracking_start_time(request->timestamp());
  event->set_timestamp(request->timestamp());

  AllocationEvent::Allocation* alloc = event->mutable_alloc_data();
  alloc->set_tag(tag);
  alloc->set_class_tag(class_tag);
  alloc->set_size(size);
  alloc->set_length(length);

  return JVMTI_VISIT_OBJECTS;
}

void MemoryAgent::ClassPrepareCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                       jthread thread, jclass klass) {
  int64_t time = agent_->clock_.GetCurrentTime();
  RecordAllocationEventsRequest record_request;
  AllocationEvent* event = record_request.add_events();
  agent_->RegisterNewClass(jni, klass, event);
  event->set_tracking_start_time(agent_->last_tracking_start_ns_);
  event->set_timestamp(time);
  record_request.set_process_id(agent_->app_id_);
  profiler::EnqueueAllocationEvents(record_request);
}

void MemoryAgent::ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                      jthread thread, jobject object,
                                      jclass klass, jlong size) {
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

  long tag = agent_->GetNextObjectTag();
  error = jvmti->SetTag(object, tag);
  CheckJvmtiError(jvmti, error);

  // TODO add to queue
}

void MemoryAgent::ObjectFreeCallback(jvmtiEnv* jvmti, jlong tag) {
  // TODO add to queue
}

void MemoryAgent::GCStartCallback(jvmtiEnv* jvmti) { agent_->LogGcStart(); }

void MemoryAgent::GCFinishCallback(jvmtiEnv* jvmti) { agent_->LogGcFinish(); }

}  // namespace profiler
