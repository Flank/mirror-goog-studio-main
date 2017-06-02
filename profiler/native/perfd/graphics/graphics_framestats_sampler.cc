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
#include "graphics_framestats_sampler.h"

#include <cstdlib>
#include <iostream>
#include <sstream>

#include "utils/log.h"
#include "utils/trace.h"

using profiler::proto::GraphicsData;

namespace profiler {

using std::vector;

void GraphicsFrameStatsSampler::GetFrameStatsVector(
    std::string &app_and_activity_name, int64_t sdk,
    std::vector<GraphicsData> &data_vector) {
  Trace trace("GRAPHICS:GetFrameStats");
  GetDumpsysCommand(app_and_activity_name, sdk);
  // TODO: Use dumpsys command to retrieve fps samples.
}

std::string GraphicsFrameStatsSampler::GetDumpsysCommand(
    std::string app_and_activity_name, int64_t sdk) {
  std::string cmd("dumpsys SurfaceFlinger --latency \"SurfaceView");
  if (sdk >= 24) {
    cmd.append(" - ").append(app_and_activity_name);
  }
  return cmd.append("\"");
}

}  // namespace profiler