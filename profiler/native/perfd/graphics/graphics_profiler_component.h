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

#include "perfd/graphics/graphics_service.h"
#include "perfd/profiler_component.h"

namespace profiler {

class GraphicsProfilerComponent final : public ProfilerComponent {
 public:
  explicit GraphicsProfilerComponent(Daemon::Utilities *utilities)
      : public_service_(utilities, &collectors_) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service *GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., agent).
  grpc::Service *GetInternalService() override { return nullptr; }

 private:
  GraphicsServiceImpl public_service_;
  // Mapping "app/activity"->GraphicsCollector.
  std::map<std::string, GraphicsCollector> collectors_;
};

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_PROFILER_COMPONENT_H_
