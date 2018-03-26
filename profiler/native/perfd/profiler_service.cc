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
  // Make sure the pid is valid.
  string app_name = ProcessManager::GetCmdlineForPid(request->pid());
  if (app_name.empty()) {
    return Status(StatusCode::NOT_FOUND,
                  "Process isn't running. Cannot create session.");
  }

  int64_t start_timestamp = daemon_->clock()->GetCurrentTime();
  for (const auto& component : daemon_->GetComponents()) {
    start_timestamp = std::min(start_timestamp,
                               component->GetEarliestDataTime(request->pid()));
  }
  daemon_->sessions()->BeginSession(request->device_id(), request->pid(),
                                    response->mutable_session(),
                                    start_timestamp);
  if (request->jvmti_config().attach_agent()) {
    daemon_->TryAttachAppAgent(request->pid(), app_name,
                               request->jvmti_config().agent_lib_file_name());
  }

  return Status::OK;
}

Status ProfilerServiceImpl::EndSession(
    ServerContext* context, const profiler::proto::EndSessionRequest* request,
    profiler::proto::EndSessionResponse* response) {
  daemon_->sessions()->EndSession(request->session_id(),
                                  response->mutable_session());
  return Status::OK;
}

Status ProfilerServiceImpl::GetSession(
    ServerContext* context, const profiler::proto::GetSessionRequest* request,
    profiler::proto::GetSessionResponse* response) {
  daemon_->sessions()->GetSession(request->session_id(),
                                  response->mutable_session());
  return Status::OK;
}

Status ProfilerServiceImpl::GetSessions(
    ServerContext* context, const profiler::proto::GetSessionsRequest* request,
    profiler::proto::GetSessionsResponse* response) {
  auto matching_sessions = daemon_->sessions()->GetSessions(
      request->start_timestamp(), request->end_timestamp());
  for (const auto& session : matching_sessions) {
    response->add_sessions()->CopyFrom(session);
  }
  return Status::OK;
}

}  // namespace profiler
