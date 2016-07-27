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
#include "energy_service.h"

#include <cinttypes>
#include <cstdlib>

using profiler::proto::EnergyCollectionStatusResponse;
using profiler::proto::EnergyDataRequest;
using profiler::proto::EnergyDataResponse;
using profiler::proto::StartEnergyCollectionRequest;
using profiler::proto::StopEnergyCollectionRequest;

namespace profiler {

grpc::Status EnergyServiceImpl::GetData(grpc::ServerContext* context,
                                        const EnergyDataRequest* request,
                                        EnergyDataResponse* response) {
  response->set_app_id(request->app_id());
  energy_cache_.LoadEnergyData(request->start_time_excl(),
                               request->end_time_incl(), response);

  return grpc::Status::OK;
}

grpc::Status EnergyServiceImpl::StartCollection(
    grpc::ServerContext* context, const StartEnergyCollectionRequest* request,
    EnergyCollectionStatusResponse* response) {
  int reset_command_result = system(kUnplugBatteryStateCommand);
  if (reset_command_result != 0) {
    // TODO error handling
  }

  response->set_app_id(request->app_id());
  response->set_timestamp(clock_.GetCurrentTime());
  // TODO this will be changed to get actual UID by pid-to-uid CL.
  collector_.Start(10087);

  return grpc::Status::OK;
}

grpc::Status EnergyServiceImpl::StopCollection(
    grpc::ServerContext* context, const StopEnergyCollectionRequest* request,
    EnergyCollectionStatusResponse* response) {
  collector_.Stop();
  response->set_app_id(request->app_id());
  response->set_timestamp(clock_.GetCurrentTime());

  return grpc::Status::OK;
}

}  // namespace profiler
