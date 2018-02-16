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

namespace profiler {

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::AddEnergyEventRequest;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::EnergyEvent;

InternalEnergyServiceImpl::InternalEnergyServiceImpl(
    EnergyCache *energy_cache, FileCache *file_cache)
    : energy_cache_(*energy_cache), file_cache_(*file_cache) {}

Status InternalEnergyServiceImpl::AddEnergyEvent(
    ServerContext *context, const AddEnergyEventRequest *request,
    EmptyEnergyReply *reply) {
  std::string trace_id;
  if (!request->callstack().empty()) {
    trace_id = file_cache_.AddString(request->callstack());
  }
  auto event = energy_cache_.AddEnergyEvent(request->energy_event());
  event->set_trace_id(trace_id);
  return Status::OK;
}

}  // namespace profiler
