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
#ifndef PERFD_IO_INTERNAL_IO_SERVICE_H_
#define PERFD_IO_INTERNAL_IO_SERVICE_H_

#include <grpc++/grpc++.h>

#include "proto/internal_io.grpc.pb.h"

#include "perfd/io/io_cache.h"
#include "perfd/io/io_speed_cache.h"

namespace profiler {
// Implements internal_io.proto file
class InternalIoServiceImpl final : public proto::InternalIoService::Service {
 public:
  InternalIoServiceImpl(IoCache *io_cache, IoSpeedCache *io_speed_cache);
  // Called when an I/O call happens. should send the information to |io_cache_|
  // to be saved.
  grpc::Status TrackIoCall(grpc::ServerContext *context,
                           const proto::IoCallRequest *io_call_request,
                           proto::EmptyIoReply *reply) override;
  // Called when a file is opened, should create a new session and send it to
  // |io_cache_|.
  grpc::Status TrackIoSessionStart(
      grpc::ServerContext *context,
      const proto::IoSessionStartRequest *io_session_start_request,
      proto::EmptyIoReply *reply) override;
  // Called when a file is closed, should terminate the saved session in
  // |io_cache_|.
  grpc::Status TrackIoSessionEnd(
      grpc::ServerContext *context,
      const proto::IoSessionEndRequest *io_session_end_request,
      proto::EmptyIoReply *reply) override;

 private:
  IoCache &io_cache_;
  IoSpeedCache &io_speed_cache_;
};

}  // namespace profiler

#endif  // PERFD_IO_INTERNAL_IO_SERVICE_H
