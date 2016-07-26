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
#ifndef PERFD_GENERIC_COMPONENT_H_
#define PERFD_GENERIC_COMPONENT_H_

#include "perfd/daemon.h"
#include "perfd/perfa_service.h"
#include "perfd/profiler_component.h"
#include "perfd/profiler_service.h"

namespace profiler {

class GenericComponent final : public ProfilerComponent {
 public:
  explicit GenericComponent(const Daemon& daemon)
      : generic_public_service_(daemon) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override {
    return &generic_public_service_;
  }

  // Returns the service that talks to device clients (e.g., perfa).
  grpc::Service* GetInternalService() override { return &perfa_service_; }

 private:
  ProfilerServiceImpl generic_public_service_;
  PerfaServiceImpl perfa_service_;
};

}  // namespace profiler

#endif  // PERFD_GENERIC_COMPONENT_H_
