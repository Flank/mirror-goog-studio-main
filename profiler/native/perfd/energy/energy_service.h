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
#ifndef PERFD_ENERGY_ENERGY_SERVICE_H_
#define PERFD_ENERGY_ENERGY_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/energy/energy_cache.h"
#include "proto/energy.grpc.pb.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;
using proto::EnergyDataResponse;
using proto::EnergyEventsResponse;
using proto::EnergyRequest;
using proto::EnergyService;

class EnergyServiceImpl final : public EnergyService::Service {
 public:
  explicit EnergyServiceImpl(EnergyCache* energy_cache)
      : energy_cache_(*energy_cache) {}

  Status GetData(ServerContext* context, const EnergyRequest* request,
                 EnergyDataResponse* response) override;

  Status GetEvents(ServerContext* context, const EnergyRequest* request,
                   EnergyEventsResponse* response) override;

 private:
  EnergyCache& energy_cache_;
};

}  // namespace profiler

#endif  // PERFD_ENERGY_ENERGY_SERVICE_H_
