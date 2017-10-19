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
#include "io_service.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

grpc::Status IoServiceImpl::GetFileData(grpc::ServerContext *context,
                                        const proto::FileDataRequest *request,
                                        proto::FileDataResponse *response) {
  std::vector<IoSessionDetails> data =
      io_cache_.GetRange(request->process_id(), request->start_timestamp(),
                         request->end_timestamp());
  for (IoSessionDetails session : data) {
    proto::FileSession file_session;
    file_session.set_io_session_id(session.session_id);
    file_session.set_start_timestamp(session.start_timestamp);
    file_session.set_end_timestamp(session.end_timestamp);
    file_session.set_file_path(session.file_path);
    for (const auto &io_call : session.calls) {
      proto::IoCall new_io_call;
      new_io_call.set_start_timestamp(io_call.start_timestamp);
      new_io_call.set_end_timestamp(io_call.end_timestamp);
      new_io_call.set_bytes_count(io_call.bytes_count);
      new_io_call.set_type(io_call.type);
      *(file_session.add_io_calls()) = new_io_call;
    }
    *(response->add_file_sessions()) = file_session;
  }
  response->set_status(proto::FileDataResponse::SUCCESS);
  return Status::OK;
}

void IoServiceImpl::AddSpeedData(const proto::SpeedDataRequest *request,
                                 profiler::proto::IoType type,
                                 proto::SpeedDataResponse *response) {
  std::vector<IoSpeedDetails> data = io_speed_cache_.GetSpeedData(
      request->process_id(), request->start_timestamp(),
      request->end_timestamp(), type);
  for (IoSpeedDetails speed_data : data) {
    proto::IoSpeedData new_speed_data;
    new_speed_data.set_type(type);
    new_speed_data.set_speed(speed_data.speed);
    new_speed_data.mutable_basic_info()->set_process_id(request->process_id());
    new_speed_data.mutable_basic_info()->mutable_session()->set_device_serial(
        request->session().device_serial());
    new_speed_data.mutable_basic_info()->mutable_session()->set_boot_id(
        request->session().boot_id());
    new_speed_data.mutable_basic_info()->set_end_timestamp(
        speed_data.timestamp);
    *(response->add_io_speed_data()) = new_speed_data;
  }
}

grpc::Status IoServiceImpl::GetSpeedData(grpc::ServerContext *context,
                                         const proto::SpeedDataRequest *request,
                                         proto::SpeedDataResponse *response) {
  if (request->type() == proto::SpeedDataRequest::ALL_SPEED_DATA ||
      request->type() == proto::SpeedDataRequest::READ_SPEED_DATA) {
    AddSpeedData(request, profiler::proto::READ, response);
  }

  if (request->type() == proto::SpeedDataRequest::ALL_SPEED_DATA ||
      request->type() == profiler::proto::SpeedDataRequest::WRITE_SPEED_DATA) {
    AddSpeedData(request, proto::WRITE, response);
  }

  response->set_status(proto::SpeedDataResponse::SUCCESS);
  return Status::OK;
}

grpc::Status IoServiceImpl::StartMonitoringApp(
    grpc::ServerContext *context, const proto::IoStartRequest *request,
    proto::IoStartResponse *response) {
  io_cache_.AllocateAppCache(request->process_id());
  io_speed_cache_.AllocateAppCache(request->process_id());
  return Status::OK;
}

grpc::Status IoServiceImpl::StopMonitoringApp(
    grpc::ServerContext *context, const proto::IoStopRequest *request,
    proto::IoStopResponse *response) {
  io_cache_.DeallocateAppCache(request->process_id());
  io_speed_cache_.AllocateAppCache(request->process_id());
  return Status::OK;
}
}  // namespace profiler