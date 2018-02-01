/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "perfd/energy/internal_energy_service.h"

#include "utils/log.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::AddEnergyEventRequest;
using profiler::proto::EmptyEnergyReply;

InternalEnergyServiceImpl::InternalEnergyServiceImpl() {}

Status InternalEnergyServiceImpl::AddEnergyEvent(
    ServerContext *context, const AddEnergyEventRequest *request,
    EmptyEnergyReply *reply) {
  auto energy_event = request->energy_event();
  Log::V("AddEnergyEvent (type=%d, timestamp=%lld)", energy_event.event_type(),
         energy_event.timestamp());
  return Status::OK;
}

}  // namespace profiler
