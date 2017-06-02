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
#ifndef PERFD_GRAPHICS_GRAPHICS_FRAMESTATS_SAMPLER_H_
#define PERFD_GRAPHICS_GRAPHICS_FRAMESTATS_SAMPLER_H_

#include <string>

#include "proto/graphics.pb.h"

namespace profiler {

class GraphicsFrameStatsSampler {
 public:
  void GetFrameStatsVector(
      std::string& app_and_activity_name, int64_t sdk,
      std::vector<profiler::proto::GraphicsData>& data_vector);
  std::string GetDumpsysCommand(std::string app_and_activity_name, int64_t sdk);
};

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_FRAMESTATS_SAMPLER_H_
