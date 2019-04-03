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
#include "agent/agent.h"
#include "agent/jni_wrappers.h"

namespace {
using profiler::EventManager;
using std::mutex;
}  // namespace

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::proto::EmptyEventResponse;
using profiler::proto::InternalEventService;
using profiler::proto::SendActivityDataRequest;
using profiler::proto::ViewData;
using std::lock_guard;

namespace profiler {

EventManager& EventManager::Instance() {
  static EventManager* instance = new EventManager();
  return *instance;
}

EventManager::EventManager() {
  Agent::Instance().AddDaemonStatusChangedCallback(std::bind(
      &profiler::EventManager::PerfdStateChanged, this, std::placeholders::_1));
}

void EventManager::CacheAndEnqueueActivityEvent(
    const profiler::proto::SendActivityDataRequest& request) {
  lock_guard<mutex> guard(activity_cache_mutex_);
  // We may have multiple active activities / fragments, so we cache all
  // that are not destroyed. When we get to this state, we no longer need to
  // cache the component.
  auto& data = request.data();
  if (data.state_changes(data.state_changes_size() - 1).state() ==
      ViewData::DESTROYED) {
    hash_activity_cache_.erase(data.hash());
  } else {
    hash_activity_cache_[data.hash()] = request;
  }
  EnqueueActivityEvent(request);
}

void EventManager::EnqueueActivityEvent(
    const profiler::proto::SendActivityDataRequest& request) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    return;
  }

  Agent::Instance().SubmitEventTasks(
      {[request](InternalEventService::Stub& stub, ClientContext& ctx) {
        EmptyEventResponse response;
        return stub.SendActivity(&ctx, request, &response);
      }});
}

void EventManager::PerfdStateChanged(bool becomes_alive) {
  if (becomes_alive) {
    lock_guard<mutex> guard(activity_cache_mutex_);
    for (const auto& map : hash_activity_cache_) {
      EnqueueActivityEvent(map.second);
    }
    hash_activity_cache_.clear();
  }
}
}  // namespace profiler