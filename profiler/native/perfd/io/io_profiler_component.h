/*
 * Copyright (C) 2017 The Android Open Source Project
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
#ifndef PERFD_IO_IO_PROFILER_COMPONENT_H_
#define PERFD_IO_IO_PROFILER_COMPONENT_H_

#include "perfd/daemon.h"
#include "perfd/io/internal_io_service.h"
#include "perfd/io/io_service.h"
#include "perfd/profiler_component.h"

namespace profiler {

class IoProfilerComponent final : public ProfilerComponent {
 public:
  explicit IoProfilerComponent()
      : public_service_(&io_cache_, &io_speed_cache_),
        internal_service_(&io_cache_, &io_speed_cache_) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &internal_service_; }

 private:
  IoCache io_cache_;
  IoSpeedCache io_speed_cache_;
  IoServiceImpl public_service_;
  InternalIoServiceImpl internal_service_;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_PROFILER_COMPONENT_H_
