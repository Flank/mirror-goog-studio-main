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
#ifndef PERFD_ENERGY_PROFILER_COMPONENT_H_
#define PERFD_ENERGY_PROFILER_COMPONENT_H_

#include <grpc++/grpc++.h>
#include "perfd/energy/internal_energy_service.h"
#include "perfd/profiler_component.h"
#include "proto/internal_energy.grpc.pb.h"

namespace profiler {

class EnergyProfilerComponent final : public ProfilerComponent {
 public:
  EnergyProfilerComponent() {}

  // Returns the service that talks to desktop clients (e.g. Studio).
  grpc::Service* GetPublicService() override { return nullptr; }

  // Returns the service that talks to device client (e.g. the agent).
  grpc::Service* GetInternalService() override { return &internal_service_; }

 private:
  InternalEnergyServiceImpl internal_service_;
};
}

#endif //PERFD_ENERGY_PROFILER_COMPONENT_H_
