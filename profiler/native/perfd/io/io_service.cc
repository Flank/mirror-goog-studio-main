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

void IoServiceImpl::AddSpeedData(const proto::IoDataRequest *request,
                                 profiler::proto::IoType type,
                                 proto::IoDataResponse *response) {
  std::vector<SpeedDetails> data = io_speed_cache_.GetSpeedData(
      request->process_id(), request->start_timestamp(),
      request->end_timestamp(), type);
  for (SpeedDetails speed_data : data) {
    proto::IoDataResponse::IoData io_speed_data;
    io_speed_data.mutable_speed_data()->set_type(type);
    io_speed_data.mutable_speed_data()->set_speed(speed_data.speed);
    io_speed_data.mutable_basic_info()->set_process_id(request->process_id());
    io_speed_data.mutable_basic_info()->mutable_session()->set_device_serial(
        request->session().device_serial());
    io_speed_data.mutable_basic_info()->mutable_session()->set_boot_id(
        request->session().boot_id());
    io_speed_data.mutable_basic_info()->set_end_timestamp(speed_data.timestamp);
    *(response->add_io_data()) = io_speed_data;
  }
}

void IoServiceImpl::AddFileData(const proto::IoDataRequest *request,
                                proto::IoDataResponse *response) {
  std::vector<SessionDetails> data =
      io_cache_.GetRange(request->process_id(), request->start_timestamp(),
                         request->end_timestamp());
  proto::IoDataResponse::IoData io_file_data;
  for (SessionDetails session : data) {
    proto::IoDataResponse::FileData::FileSession file_session;
    file_session.set_io_session_id(session.session_id);
    file_session.set_start_timestamp(session.start_timestamp);
    file_session.set_end_timestamp(session.end_timestamp);
    file_session.set_file_path(session.file_path);
    for (const auto &io_call : session.calls) {
      proto::IoDataResponse::FileData::FileSession::IoCall new_io_call;
      new_io_call.set_start_timestamp(io_call.start_timestamp);
      new_io_call.set_end_timestamp(io_call.end_timestamp);
      new_io_call.set_bytes_count(io_call.bytes_count);
      new_io_call.set_type(io_call.type);
      *(file_session.add_io_calls()) = new_io_call;
    }
    *(io_file_data.mutable_file_data()->add_file_sessions()) = file_session;
  }
  io_file_data.mutable_basic_info()->set_process_id(request->process_id());
  // TODO: Add an enum value UNUSED to message CommonData in profiler.proto
  io_file_data.mutable_basic_info()->set_end_timestamp(-1);
  io_file_data.mutable_basic_info()->mutable_session()->set_device_serial(
      request->session().device_serial());
  io_file_data.mutable_basic_info()->mutable_session()->set_boot_id(
      request->session().boot_id());
  *(response->add_io_data()) = io_file_data;
}

grpc::Status IoServiceImpl::GetData(grpc::ServerContext *context,
                                    const proto::IoDataRequest *request,
                                    proto::IoDataResponse *response) {
  if (request->type() == proto::IoDataRequest::ALL_DATA ||
      request->type() == proto::IoDataRequest::FILE_DATA) {
    AddFileData(request, response);
  }

  if (request->type() == proto::IoDataRequest::ALL_DATA ||
      request->type() == proto::IoDataRequest::ALL_SPEED_DATA ||
      request->type() == proto::IoDataRequest::READ_SPEED_DATA) {
    AddSpeedData(request, profiler::proto::READ, response);
  }

  if (request->type() == proto::IoDataRequest::ALL_DATA ||
      request->type() == proto::IoDataRequest::ALL_SPEED_DATA ||
      request->type() == profiler::proto::IoDataRequest::WRITE_SPEED_DATA) {
    AddSpeedData(request, proto::WRITE, response);
  }

  response->set_status(proto::IoDataResponse::SUCCESS);
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