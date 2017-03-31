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
#ifndef PERFD_NETWORK_NETWORK_PROFILER_COMPONENT_H_
#define PERFD_NETWORK_NETWORK_PROFILER_COMPONENT_H_

#include "perfd/daemon.h"
#include "perfd/network/internal_network_service.h"
#include "perfd/network/network_collector.h"
#include "perfd/network/network_service.h"
#include "perfd/profiler_component.h"
#include "utils/file_cache.h"

namespace profiler {

class NetworkProfilerComponent final : public ProfilerComponent {
 public:
  explicit NetworkProfilerComponent(Daemon::Utilities* utilities)
      : public_service_(&network_cache_),
        internal_service_(utilities, &network_cache_) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &internal_service_; }

 private:
  NetworkCache network_cache_;
  NetworkServiceImpl public_service_;
  InternalNetworkServiceImpl internal_service_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_PROFILER_COMPONENT_H_
