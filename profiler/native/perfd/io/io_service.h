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
#ifndef PERFD_IO_IO_PROFILER_SERVICE_H_
#define PERFD_IO_IO_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/io/io_cache.h"
#include "perfd/io/io_speed_cache.h"
#include "proto/io.grpc.pb.h"

namespace profiler {

// Service class to pass profiler data through grpc.
class IoServiceImpl final : public proto::IoService::Service {
 public:
  explicit IoServiceImpl(IoCache *io_cache, IoSpeedCache *io_speed_cache)
      : io_cache_(*io_cache), io_speed_cache_(*io_speed_cache) {}

  grpc::Status GetData(grpc::ServerContext *context,
                       const proto::IoDataRequest *request,
                       proto::IoDataResponse *response) override;

  grpc::Status StartMonitoringApp(grpc::ServerContext *context,
                                  const proto::IoStartRequest *request,
                                  proto::IoStartResponse *response) override;

  grpc::Status StopMonitoringApp(grpc::ServerContext *context,
                                 const proto::IoStopRequest *request,
                                 proto::IoStopResponse *response) override;

 private:
  IoCache &io_cache_;
  IoSpeedCache &io_speed_cache_;

  void AddSpeedData(const proto::IoDataRequest *request,
                    profiler::proto::IoType type,
                    proto::IoDataResponse *response);
  void AddFileData(const proto::IoDataRequest *request,
                   proto::IoDataResponse *response);
};

}  // namespace profiler

#endif  // PERFD_IO_IO_PROFILER_SERVICE_H_
