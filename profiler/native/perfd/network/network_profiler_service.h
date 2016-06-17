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
#ifndef PERFD_NETWORK_NETWORK_PROFILER_SERVICE_H_
#define PERFD_NETWORK_NETWORK_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>
#include <memory>
#include <vector>

#include "perfd/network/network_collector.h"
#include "proto/network_profiler.grpc.pb.h"
#include "utils/time_value_buffer.h"

namespace profiler {

// Service class to pass profiler data through grpc.
class NetworkProfilerServiceImpl final
    : public proto::NetworkProfilerService::Service {
 public:
  NetworkProfilerServiceImpl();

  grpc::Status GetData(grpc::ServerContext *context,
                       const proto::NetworkDataRequest *request,
                       proto::NetworkDataResponse *response) override;

  grpc::Status StartMonitoringApp(
      grpc::ServerContext *context, const proto::NetworkStartRequest *request,
      proto::NetworkStartResponse *response) override;

  grpc::Status StopMonitoringApp(grpc::ServerContext *context,
                                 const proto::NetworkStopRequest *request,
                                 proto::NetworkStopResponse *response) override;

 private:
  // Start sampling data for device network information (pid == -1), or sampling
  // data for a given app.
  void StartCollector(int pid);

  // Max number of profiler data instances that a buffer can hold.
  static const int kBufferCapacity = 10 * 60 * 10;

  // TODO: The vectors may need mutex.
  std::vector<std::unique_ptr<NetworkProfilerBuffer>> buffers_;
  std::vector<std::unique_ptr<NetworkCollector>> collectors_;
};

}  // namespace profiler

#endif // PERFD_NETWORK_NETWORK_PROFILER_SERVICE_H_
