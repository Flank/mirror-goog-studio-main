/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "daemon/transport_service.h"
#include "daemon/event_writer.h"

#include <sys/time.h>
#include "daemon/daemon.h"
#include "utils/android_studio_version.h"
#include "utils/file_reader.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::ServerWriter;
using grpc::Status;
using grpc::StatusCode;
using std::string;

namespace profiler {
/**
 * Helper class to wrap the EventWriter interface. This class is passed to the
 * EventBuffer and forwards any events to the attached ServerWriter.
 */
class ServerEventWriter final : public EventWriter {
 public:
  ServerEventWriter(ServerWriter<proto::Event>& writer) : writer_(writer) {}
  bool Write(const proto::Event& event) override {
    return writer_.Write(event);
  }

 private:
  ServerWriter<proto::Event>& writer_;
};

Status TransportServiceImpl::GetCurrentTime(ServerContext* context,
                                            const proto::TimeRequest* request,
                                            proto::TimeResponse* response) {
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

Status TransportServiceImpl::GetVersion(ServerContext* context,
                                        const proto::VersionRequest* request,
                                        proto::VersionResponse* response) {
  response->set_version(profiler::kAndroidStudioVersion);
  return Status::OK;
}

Status TransportServiceImpl::GetBytes(ServerContext* context,
                                      const proto::BytesRequest* request,
                                      proto::BytesResponse* response) {
  auto* file_cache = daemon_->file_cache();
  response->set_contents(file_cache->GetFile(request->id())->Contents());
  return Status::OK;
}

Status TransportServiceImpl::GetAgentStatus(
    ServerContext* context, const proto::AgentStatusRequest* request,
    proto::AgentData* response) {
  response->set_status(daemon_->GetAgentStatus(request->pid()));
  return Status::OK;
}

Status TransportServiceImpl::ConfigureStartupAgent(
    ServerContext* context, const proto::ConfigureStartupAgentRequest* request,
    proto::ConfigureStartupAgentResponse* response) {
  return daemon_->ConfigureStartupAgent(request, response);
}

Status TransportServiceImpl::Execute(ServerContext* context,
                                     const proto::ExecuteRequest* request,
                                     proto::ExecuteResponse* response) {
  return daemon_->Execute(request->command());
}

Status TransportServiceImpl::GetEvents(ServerContext* context,
                                       const proto::GetEventsRequest* request,
                                       ServerWriter<proto::Event>* response) {
  ServerEventWriter writer(*response);
  daemon_->WriteEventsTo(&writer);
  // Only return when a connection between the client and server is terminated.
  return Status::OK;
}

Status TransportServiceImpl::GetEventGroups(
    ServerContext* context, const proto::GetEventGroupsRequest* request,
    proto::GetEventGroupsResponse* response) {
  for (auto& group : daemon_->GetEventGroups(request)) {
    proto::EventGroup* event_group = response->add_groups();
    event_group->CopyFrom(group);
  }
  return Status::OK;
}

}  // namespace profiler
