/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef PERFD_PROFILER_COMPONENT_H_
#define PERFD_PROFILER_COMPONENT_H_

#include "daemon/service_component.h"
#include "perfd/profiler_service.h"

namespace profiler {

class Daemon;

class CommonProfilerComponent final : public ServiceComponent {
 public:
  explicit CommonProfilerComponent(Daemon* daemon) : public_service_(daemon) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  grpc::Service* GetInternalService() override { return nullptr; }

 private:
  ProfilerServiceImpl public_service_;
};

}  // namespace profiler

#endif  // PERFD_PROFILER_COMPONENT_H_