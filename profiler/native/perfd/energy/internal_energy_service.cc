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
#include "internal_energy_service.h"

#include <cstdio>
#include <cstdlib>

#include <grpc++/grpc++.h>
#include <unistd.h>

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

Status InternalEnergyServiceImpl::RecordWakeLockEvent(
    ServerContext *context, const proto::RecordWakeLockEventRequest *request,
    proto::EmptyEnergyReply *reply) {
  energy_cache_->SaveWakeLockEvent(request->event());

  return Status::OK;
}

}  // namespace profiler

