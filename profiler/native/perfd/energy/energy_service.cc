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

namespace profiler {

using grpc::ServerContext;
using grpc::Status;
using proto::EnergyDataResponse;
using proto::EnergyEventsResponse;
using proto::EnergyRequest;
using proto::EnergyService;
using proto::EnergyStartRequest;
using proto::EnergyStartResponse;
using proto::EnergyStopRequest;
using proto::EnergyStopResponse;

Status EnergyServiceImpl::StartMonitoringApp(ServerContext* context,
                                             const EnergyStartRequest* request,
                                             EnergyStartResponse* response) {
  // TODO (b/73116415): Only cache samples/events if we've started
  return Status::OK;
}

Status EnergyServiceImpl::StopMonitoringApp(ServerContext* context,
                                            const EnergyStopRequest* request,
                                            EnergyStopResponse* response) {
  // TODO (b/73116415): Stop monitoring energy samples/events
  return Status::OK;
}

Status EnergyServiceImpl::GetData(ServerContext* context,
                                  const EnergyRequest* request,
                                  EnergyDataResponse* response) {
  return Status::OK;
}

Status EnergyServiceImpl::GetEvents(ServerContext* context,
                                    const EnergyRequest* request,
                                    EnergyEventsResponse* response) {
  auto energy_events = energy_cache_.GetEnergyEvents(request->session().pid(),
                                                     request->start_timestamp(),
                                                     request->end_timestamp());
  for (const auto& energy_event : energy_events) {
    response->add_event()->CopyFrom(energy_event);
  }
  return Status::OK;
}

}  // namespace profiler