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
#ifndef PERFD_GRAPHICS_GRAPHICS_PROFILER_COMPONENT_H_
#define PERFD_GRAPHICS_GRAPHICS_PROFILER_COMPONENT_H_

#include <map>
#include <string>

#include "daemon/service_component.h"
#include "perfd/graphics/graphics_service.h"

namespace profiler {

class GraphicsProfilerComponent final : public ServiceComponent {
 public:
  explicit GraphicsProfilerComponent(Clock* clock) : public_service_(clock) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., agent).
  grpc::Service* GetInternalService() override { return nullptr; }

 private:
  GraphicsServiceImpl public_service_;
};

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_PROFILER_COMPONENT_H_
