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
#ifndef PERFD_ENERGY_INTERNAL_ENERGY_SERVICE_H_
#define PERFD_ENERGY_INTERNAL_ENERGY_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/energy/energy_cache.h"
#include "proto/internal_energy.grpc.pb.h"
#include "utils/file_cache.h"

namespace profiler {

class InternalEnergyServiceImpl final
    : public proto::InternalEnergyService::Service {
 public:
  InternalEnergyServiceImpl(EnergyCache* energy_cache, FileCache* file_cache);

  // RPC to send a wake lock acquire or release event.
  grpc::Status AddEnergyEvent(grpc::ServerContext* context,
                              const proto::AddEnergyEventRequest* request,
                              proto::EmptyEnergyReply* reply) override;

 private:
  EnergyCache& energy_cache_;
  FileCache &file_cache_;
};

}  // namespace profiler

#endif  // PERFD_ENERGY_INTERNAL_ENERGY_SERVICE_H_
