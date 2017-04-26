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

#include "perfd/network/network_cache.h"
#include "perfd/network/network_collector.h"
#include "proto/network.grpc.pb.h"
#include "utils/time_value_buffer.h"

namespace profiler {

// Service class to pass profiler data through grpc.
class NetworkServiceImpl final : public proto::NetworkService::Service {
 public:
  explicit NetworkServiceImpl(NetworkCache *network_cache);

  grpc::Status GetData(grpc::ServerContext *context,
                       const proto::NetworkDataRequest *request,
                       proto::NetworkDataResponse *response) override;

  grpc::Status StartMonitoringApp(
      grpc::ServerContext *context, const proto::NetworkStartRequest *request,
      proto::NetworkStartResponse *response) override;

  grpc::Status StopMonitoringApp(grpc::ServerContext *context,
                                 const proto::NetworkStopRequest *request,
                                 proto::NetworkStopResponse *response) override;

  grpc::Status GetHttpRange(grpc::ServerContext *context,
                            const proto::HttpRangeRequest *httpRange,
                            proto::HttpRangeResponse *response) override;

  grpc::Status GetHttpDetails(grpc::ServerContext *context,
                              const proto::HttpDetailsRequest *httpDetails,
                              proto::HttpDetailsResponse *response) override;

 private:
  // Start sampling data for device network information.
  void StartDeviceCollector();
  // Start sampling data for a given app (non-zero |pid|).
  void StartAppCollector(int32_t pid);
  void StartCollectorFor(NetworkProfilerBuffer *buffer, int32_t sample_rate_ms);

  // Max number of an app's profiler data instances. Polling rate of read
  // data to profiler is less than 1 second, 10 seconds is enough to hold
  // and 1024 is consistent with memory_levels_sampler.
  static const int kBufferCapacity = 1024;

  // TODO: The vectors may need mutex.
  std::unique_ptr<NetworkProfilerBuffer> device_buffer_;
  std::vector<std::unique_ptr<NetworkProfilerBuffer>> app_buffers_;
  std::vector<std::unique_ptr<NetworkCollector>> collectors_;

  NetworkCache &network_cache_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_PROFILER_SERVICE_H_
