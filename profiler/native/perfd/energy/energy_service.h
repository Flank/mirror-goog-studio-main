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
#ifndef PROFILER_PERFD_ENERGY_ENERGY_SERVICE_H_
#define PROFILER_PERFD_ENERGY_ENERGY_SERVICE_H_

#include <grpc++/grpc++.h>

#include "energy_cache.h"
#include "energy_collector.h"
#include "proto/energy.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class EnergyServiceImpl final : public proto::EnergyService::Service {
 public:
  explicit EnergyServiceImpl(const Clock& clock, EnergyCache* energy_cache)
      : clock_(clock),
        energy_cache_(energy_cache),
        collector_(clock, energy_cache_) {}
  virtual ~EnergyServiceImpl() = default;

  grpc::Status GetWakeLockData(grpc::ServerContext* context,
                               const proto::WakeLockDataRequest* request,
                               proto::WakeLockDataResponse* response) override;

  grpc::Status GetEnergyData(grpc::ServerContext* context,
                             const proto::EnergyDataRequest* request,
                             proto::EnergyDataResponse* response) override;

  grpc::Status StartCollection(
      grpc::ServerContext* context,
      const proto::StartEnergyCollectionRequest* request,
      proto::EnergyCollectionStatusResponse* response) override;

  grpc::Status StopCollection(
      grpc::ServerContext* context,
      const proto::StopEnergyCollectionRequest* request,
      proto::EnergyCollectionStatusResponse* response) override;

 private:
  // This command does the following:
  // 1. Perform a reset on battey . This resets batterystats and clears any
  //    previously changed state that could interfere with energy profiling.
  // 2. Set the charging state of the battery to 0 (false) for all power sources
  //    (usb, ac, and wireless) so system will think the device is not being
  //    charged even if it's plugged in.
  static constexpr const char* kUnplugBatteryStateCommand =
      "dumpsys battery reset && dumpsys battery set usb 0 && dumpsys battery "
      "set ac 0 && dumpsys battery set wireless 0";
  const Clock& clock_;
  EnergyCache* energy_cache_;
  EnergyCollector collector_;
};
}  // namespace profiler

#endif  // PROFILER_PERFD_ENERGY_ENERGY_SERVICE_H_
