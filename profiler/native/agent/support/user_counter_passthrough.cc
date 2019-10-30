/*
 * Copyright (C) 2009 The Android Open Source Project
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
 *
 */
#include <jni.h>
#include <unistd.h>

#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "event_manager.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::AgentService;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::SendEventRequest;
using profiler::proto::UserCounterData;

using profiler::JStringWrapper;
using profiler::proto::EmptyEventResponse;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

Status SendUserCounterEvent(AgentService::Stub& stub, ClientContext& ctx,
                            UserCounterData* data, int32_t pid,
                            int64_t timestamp, bool is_end, int32_t hashCode) {
  SendEventRequest request;
  auto* event = request.mutable_event();
  event->set_pid(pid);
  event->set_group_id(hashCode);
  event->set_is_ended(is_end);
  event->set_kind(Event::USER_COUNTERS);
  event->set_timestamp(timestamp);
  event->mutable_user_counters()->CopyFrom(*data);

  EmptyResponse response;
  return stub.SendEvent(&ctx, request, &response);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_CustomEventProfiler_sendRecordedEvent(
    JNIEnv* env, jobject thiz, jstring jname, jint jvalue, jint jhash) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  JStringWrapper name(env, jname);

  Agent::Instance().SubmitAgentTasks({[pid, timestamp, name, jvalue, jhash](
                                          AgentService::Stub& stub,
                                          ClientContext& ctx) mutable {
    UserCounterData data;
    data.set_name(name.get());
    data.set_recorded_value(jvalue);
    return SendUserCounterEvent(stub, ctx, &data, pid, timestamp, false, jhash);
  }});
}
};
