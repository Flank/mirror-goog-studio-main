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
#include "internal_io_service.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

InternalIoServiceImpl::InternalIoServiceImpl(IoCache *io_cache,
                                             IoSpeedCache *io_speed_cache)
    : io_cache_(*io_cache), io_speed_cache_(*io_speed_cache) {}

Status InternalIoServiceImpl::TrackIoCall(
    ServerContext *context, const proto::IoCallRequest *io_call_request,
    proto::EmptyIoReply *reply) {
  auto session_details = io_cache_.GetDetails(io_call_request->process_id(),
                                              io_call_request->io_session_id());
  if (session_details != nullptr) {
    SessionDetails::IoCall io_call;
    io_call.start_timestamp = io_call_request->start_timestamp();
    io_call.end_timestamp = io_call_request->end_timestamp();
    io_call.bytes_count = io_call_request->bytes_count();
    io_call.type = io_call_request->type();
    session_details->calls.push_back(io_call);
  }
  io_speed_cache_.AddIoCall(
      io_call_request->process_id(), io_call_request->start_timestamp(),
      io_call_request->end_timestamp(), io_call_request->bytes_count(),
      io_call_request->type());
  return Status::OK;
}

Status InternalIoServiceImpl::TrackIoSessionStart(
    ServerContext *context,
    const proto::IoSessionStartRequest *io_session_start_request,
    proto::EmptyIoReply *reply) {
  io_cache_.AddSession(io_session_start_request->process_id(),
                       io_session_start_request->io_session_id(),
                       io_session_start_request->timestamp(),
                       io_session_start_request->file_path());
  return Status::OK;
}

Status InternalIoServiceImpl::TrackIoSessionEnd(
    ServerContext *context,
    const proto::IoSessionEndRequest *io_session_end_request,
    proto::EmptyIoReply *reply) {
  auto session_details =
      io_cache_.GetDetails(io_session_end_request->process_id(),
                           io_session_end_request->io_session_id());
  if (session_details != nullptr) {
    session_details->end_timestamp = io_session_end_request->timestamp();
  }
  return Status::OK;
}

}  // namespace profiler
