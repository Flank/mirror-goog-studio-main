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
#include "utils/bash_command.h"

namespace profiler {

class GraphicsFrameStatsSampler {
 public:
  // Collects stats since |start_timestamp_exclusive| (not included) obtained by
  // running command in |cmd_runner| and adds them to |data_vector|.
  // Returns the the timestamp of the last sampled frame.
  int64_t GetFrameStatsVector(
      const int64_t start_timestamp_exclusive,
      const BashCommandRunner& cmd_runner,
      std::vector<profiler::proto::GraphicsData>* data_vector);
  // Returns a dumpsys command as a string that can be used to retrieve frame
  // stats about the given |app_and_activity_name|.
  //
  // For API level 24+ the dumpsys command must contain the activity of the
  // SurfaceView it is capturing data for, or it will not be able to capture the
  // data.
  //
  // We assume only one activity is used throughout the monitoring
  // process for API 24+. If the forefront activity changes monitoring will stop
  // receiving data.
  static std::string GetDumpsysCommand(const std::string app_and_activity_name,
                                       const int64_t sdk);

 private:
  // Parses the output from the dumpsys command into the |data_vector| after
  // filtering out the frame times before the given |start_timestamp_exclusive|
  // and returns the timestamp of the last frame that was parsed.
  int64_t ParseFrameStatsOutput(const std::string frame_stats_output,
                                const int64_t start_timestamp_exclusive,
                                std::vector<proto::GraphicsData>* data_vector);
  int64_t ParseFrameStatsOutputImpl(
      const std::vector<std::string>& frame_stats_string,
      const int64_t start_timestamp_exclusive,
      std::vector<proto::GraphicsData>* data_vector);
  std::vector<std::string> Split(const std::string str, const char delimiter);
};

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_FRAMESTATS_SAMPLER_H_
