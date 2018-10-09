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

#include <sys/time.h>
#include "utils/android_studio_version.h"
#include "utils/file_reader.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::Status;
using grpc::StatusCode;
using std::string;

namespace {
// Workaround to serve legacy GetSessions API via the new, generic cache in
// which there is no device_id. This variable is set only by the legacy
// BeginSession call and used by the legacy GetSessions call.
// It's assumed perfd sees the same device ID during its lifetime.
// TODO: Remove this workaround when we delete legacy APIs.
int64_t device_id_in_last_begin_session_request = -1;
}  // namespace
namespace profiler {

Status ProfilerServiceImpl::GetCurrentTime(
    ServerContext* context, const profiler::proto::TimeRequest* request,
    profiler::proto::TimeResponse* response) {
  Trace trace("PRO:GetTimes");

  response->set_timestamp_ns(daemon_->clock()->GetCurrentTime());
  // TODO: Move this to utils.
  timeval time;
  gettimeofday(&time, nullptr);
  // Not specifying LL may cause overflow depending on the underlying type of
  // time.tv_sec.
  int64_t t = time.tv_sec * 1000000LL + time.tv_usec;
  response->set_epoch_timestamp_us(t);
  return Status::OK;
}

Status ProfilerServiceImpl::GetVersion(
    ServerContext* context, const profiler::proto::VersionRequest* request,
    profiler::proto::VersionResponse* response) {
  response->set_version(profiler::kAndroidStudioVersion);
  return Status::OK;
}

Status ProfilerServiceImpl::GetBytes(
    ServerContext* context, const profiler::proto::BytesRequest* request,
    profiler::proto::BytesResponse* response) {
  auto* file_cache = daemon_->file_cache();
  response->set_contents(file_cache->GetFile(request->id())->Contents());
  return Status::OK;
}

Status ProfilerServiceImpl::GetAgentStatus(
    ServerContext* context, const profiler::proto::AgentStatusRequest* request,
    profiler::proto::AgentStatusResponse* response) {
  daemon_->GetAgentStatus(request, response);

  return Status::OK;
}

Status ProfilerServiceImpl::GetDevices(
    ServerContext* context, const profiler::proto::GetDevicesRequest* request,
    profiler::proto::GetDevicesResponse* response) {
  Trace trace("PRO:GetDevices");
  profiler::proto::Device* device = response->add_device();
  string device_id;
  FileReader::Read("/proc/sys/kernel/random/boot_id", &device_id);
  device->set_boot_id(device_id);
  return Status::OK;
}

Status ProfilerServiceImpl::ConfigureStartupAgent(
    ServerContext* context,
    const profiler::proto::ConfigureStartupAgentRequest* request,
    profiler::proto::ConfigureStartupAgentResponse* response) {
  return daemon_->ConfigureStartupAgent(request, response);
}

Status ProfilerServiceImpl::BeginSession(
    ServerContext* context, const profiler::proto::BeginSessionRequest* request,
    profiler::proto::BeginSessionResponse* response) {
  device_id_in_last_begin_session_request = request->device_id();
  proto::Command command;
  command.set_stream_id(request->device_id());
  command.set_type(proto::Command::BEGIN_SESSION);
  proto::BeginSession* begin = command.mutable_begin_session();
  auto* jvmti_config = begin->mutable_jvmti_config();

  jvmti_config->set_attach_agent(request->jvmti_config().attach_agent());
  jvmti_config->set_agent_lib_file_name(
      request->jvmti_config().agent_lib_file_name());
  jvmti_config->set_live_allocation_enabled(
      request->jvmti_config().live_allocation_enabled());

  begin->set_pid(request->pid());
  begin->set_request_time_epoch_ms(request->request_time_epoch_ms());
  begin->set_session_name(request->session_name());

  return daemon_->Execute(command, [this, response]() {
    profiler::Session* session = daemon_->sessions()->GetLastSession();
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
  command.set_stream_id(request->device_id());
  command.mutable_end_session()->set_session_id(request->session_id());

  return daemon_->Execute(command, [this, response]() {
    profiler::Session* session = daemon_->sessions()->GetLastSession();
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
  req.set_end(proto::Event::SESSION_ENDED);
  req.set_from_timestamp(request->start_timestamp());
  req.set_to_timestamp(request->end_timestamp());
  for (auto& group : daemon_->GetEventGroups(&req)) {
    profiler::proto::Session session;
    // group ids are sessions id for session events
    session.set_session_id(group.event_id());
    for (int i = 0; i < group.events_size(); i++) {
      const auto& event = group.events(i);
      if (event.has_session_started()) {
        session.set_device_id(device_id_in_last_begin_session_request);
        session.set_pid(event.session_started().pid());
        session.set_start_timestamp(event.timestamp());
        session.set_end_timestamp(LLONG_MAX);
      }
      if (event.has_session_ended()) {
        session.set_end_timestamp(event.timestamp());
      }
    }
    response->add_sessions()->CopyFrom(session);
  }

  return Status::OK;
}

Status ProfilerServiceImpl::Execute(
    ServerContext* context, const profiler::proto::ExecuteRequest* request,
    profiler::proto::ExecuteResponse* response) {
  return daemon_->Execute(request->command());
}

Status ProfilerServiceImpl::GetEvents(
    ServerContext* context, const profiler::proto::GetEventsRequest* request,
    profiler::proto::GetEventsResponse* response) {
  for (auto& event : daemon_->GetEvents(request)) {
    response->add_events()->CopyFrom(event);
  }
  return Status::OK;
}

Status ProfilerServiceImpl::GetEventGroups(
    ServerContext* context,
    const profiler::proto::GetEventGroupsRequest* request,
    profiler::proto::GetEventGroupsResponse* response) {
  for (auto& group : daemon_->GetEventGroups(request)) {
    proto::EventGroup* event_group = response->add_groups();
    event_group->CopyFrom(group);
  }
  return Status::OK;
}

}  // namespace profiler
