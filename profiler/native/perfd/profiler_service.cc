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
#include "perfd/profiler_service.h"

#include "perfd/sessions/sessions_manager.h"

using grpc::ServerContext;
using grpc::Status;
using grpc::StatusCode;
using std::string;

namespace profiler {

Status ProfilerServiceImpl::BeginSession(
    ServerContext* context, const profiler::proto::BeginSessionRequest* request,
    profiler::proto::BeginSessionResponse* response) {
  proto::Command command;
  // In the legacy pipeline we don't have streams so use device ID instead.
  command.set_stream_id(request->device_id());
  command.set_type(proto::Command::BEGIN_SESSION);
  command.set_pid(request->pid());
  proto::BeginSession* begin = command.mutable_begin_session();
  auto* jvmti_config = begin->mutable_jvmti_config();

  jvmti_config->set_attach_agent(request->jvmti_config().attach_agent());
  jvmti_config->set_agent_lib_file_name(
      request->jvmti_config().agent_lib_file_name());
  jvmti_config->set_agent_config_path(
      request->jvmti_config().agent_config_path());
  jvmti_config->set_live_allocation_enabled(
      request->jvmti_config().live_allocation_enabled());

  begin->set_request_time_epoch_ms(request->request_time_epoch_ms());
  begin->set_session_name(request->session_name());
  begin->set_process_abi(request->process_abi());

  return daemon_->Execute(command, [response]() {
    profiler::Session* session = SessionsManager::Instance()->GetLastSession();
    if (session) {
      response->mutable_session()->CopyFrom(session->info());
    }
  });
}

Status ProfilerServiceImpl::EndSession(
    ServerContext* context, const profiler::proto::EndSessionRequest* request,
    profiler::proto::EndSessionResponse* response) {
  proto::Command command;
  command.set_type(proto::Command::END_SESSION);
  // In the legacy pipeline we don't have streams so use device ID instead.
  command.set_stream_id(request->device_id());
  command.mutable_end_session()->set_session_id(request->session_id());

  return daemon_->Execute(command, [response]() {
    profiler::Session* session = SessionsManager::Instance()->GetLastSession();
    if (session) {
      response->mutable_session()->CopyFrom(session->info());
    }
  });

  return Status::OK;
}

Status ProfilerServiceImpl::GetSessions(
    ServerContext* context, const profiler::proto::GetSessionsRequest* request,
    profiler::proto::GetSessionsResponse* response) {
  proto::GetEventGroupsRequest req;
  req.set_kind(proto::Event::SESSION);
  req.set_from_timestamp(request->start_timestamp());
  req.set_to_timestamp(request->end_timestamp());
  for (auto& group : daemon_->GetEventGroups(&req)) {
    profiler::proto::Session session;
    // group ids are sessions id for session events
    session.set_session_id(group.group_id());
    for (int i = 0; i < group.events_size(); i++) {
      const auto& event = group.events(i);
      if (event.has_session()) {
        auto session_started = event.session().session_started();
        session.set_stream_id(session_started.stream_id());
        session.set_pid(session_started.pid());
        session.set_start_timestamp(event.timestamp());
        session.set_end_timestamp(LLONG_MAX);
      }
      if (event.is_ended()) {
        session.set_end_timestamp(event.timestamp());
      }
    }
    response->add_sessions()->CopyFrom(session);
  }

  return Status::OK;
}

}  // namespace profiler
