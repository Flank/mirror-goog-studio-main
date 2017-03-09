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
#include "event_manager.h"
#include "perfa/perfa.h"
#include "perfa/support/jni_wrappers.h"

namespace {
using profiler::EventManager;
using std::mutex;
}


using grpc::ClientContext;
using profiler::Perfa;
using profiler::proto::ActivityData;
using profiler::proto::ActivityStateData;
using profiler::proto::EmptyEventResponse;
using profiler::JStringWrapper;
using std::lock_guard;


namespace profiler {

EventManager& EventManager::Instance() {
  static EventManager* instance = new EventManager();
  return *instance;
}

EventManager::EventManager() : has_cached_data_(false) {
  Perfa::Instance().AddPerfdStatusChangedCallback(std::bind(
      &profiler::EventManager::PerfdStateChanged, this, std::placeholders::_1));
}

void EventManager::CacheAndEnqueueActivityEvent(
    const profiler::proto::ActivityData& activity) {
  lock_guard<mutex> guard(cache_mutex_);
  activity_ = activity;
  has_cached_data_ = true;
  EnqueueActivityEvent(activity);
}

void EventManager::EnqueueActivityEvent(
    const profiler::proto::ActivityData& activity) {
  if (!has_cached_data_) {
    return;
  }
  Perfa::Instance().background_queue()->EnqueueTask([activity]() {
    auto event_stub = Perfa::Instance().event_stub();
    ClientContext context;
    EmptyEventResponse response;
    event_stub.SendActivity(&context, activity, &response);
  });
}

void EventManager::PerfdStateChanged(bool becomes_alive) {
  if (becomes_alive) {
    lock_guard<mutex> guard(cache_mutex_);
    EnqueueActivityEvent(activity_);
  }
}
} // namespace profiler