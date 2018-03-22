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

#include <sstream>

#include "utils/trace.h"

using profiler::proto::GraphicsData;

namespace profiler {

using std::vector;

// Frame stats times will only be present if a SurfaceView activity is on
// screen. A SurfaceView is a special subclass of View that offers a dedicated
// drawing surface within the View hierarchy driven by an application's
// secondary thread.
// See https://developer.android.com/guide/topics/graphics/2d-graphics.html
//
// The dumpsys command used to retrieve the frame stats only reports the last
// 127 frames. Sampling must be done at a greater frequency than every two
// seconds (frame rendering is limited by VSYNC to 60 frames/sec) to capture
// data about every frame. When sampling at more than every two seconds
// previous frames will be filtered from the result. This is done by returning
// the last sampled timestamp to be used in subsequent calls to this
// function.
//
// The command used to retrieve the frame stats data outputs:
//
// Single number in first column that is device refresh rate (in Milliseconds)
// 127 lines that contain 3 values - each line is the time data from a
// specific frame
//    Value 1: The app draw time (When the app started to draw).
//    Value 2: The VSYNC timestamp just after the call to set (The VSYNC
//      immediately after SurfaceFlinger started submitting the frame to the
//      h/w.
//    Value 3: The timestamp of the call to set (Timestamp immediately after
//      SF submitted that frame to the h/w).
//
// Example:
// 16666667
// 96070354631117	96070372447472	96070354631117
// ... the other 126 lines of data in the same format ...
//
int64_t GraphicsFrameStatsSampler::GetFrameStatsVector(
    const int64_t start_timestamp_exclusive,
    const BashCommandRunner& cmd_runner,
    std::vector<GraphicsData>* data_vector) {
  Trace trace("GRAPHICS:GetFrameStats");
  // Get the frame stats output from the dumpsys command.
  std::string output;
  cmd_runner.Run("", &output);
  // Parse the output to get the data and add it to the |data_vector|.
  return ParseFrameStatsOutput(output, start_timestamp_exclusive, data_vector);
}

std::string GraphicsFrameStatsSampler::GetDumpsysCommand() {
  std::string forefront_activity = GetForefrontActivity();
  if (forefront_activity.empty()) {
    // This happens if there is no SurfaceView on the screen.
    return "";
  }
  std::string cmd("dumpsys SurfaceFlinger --latency \"");
  cmd.append(forefront_activity).append("\"");
  return cmd;
}

std::string GraphicsFrameStatsSampler::GetForefrontActivity() {
  std::string output;
  BashCommandRunner cmd_get_forefront{
      "dumpsys SurfaceFlinger --list | grep SurfaceView | grep -v "
      "'Background "
      "for'"};
  cmd_get_forefront.Run("", &output);
  if (output.empty()) {
    return "";
  }
  // Some apis produce duplicate lines for the forefront activity.
  // Just take the first line.
  output = Split(output, '\n').front();
  if (isspace(output.back())) {
    output.pop_back();
  }
  return output;
}

int64_t GraphicsFrameStatsSampler::ParseFrameStatsOutput(
    const std::string frame_stats_output,
    const int64_t start_timestamp_exclusive,
    std::vector<proto::GraphicsData>* data_vector) {
  const int64_t local_start_timestamp_exclusive = start_timestamp_exclusive;
  if (frame_stats_output == "") {
    return local_start_timestamp_exclusive;
  }
  std::vector<std::string> frame_stats_output_vector =
      Split(frame_stats_output, '\n');

  if (frame_stats_output_vector.size() < 2) {
    return local_start_timestamp_exclusive;
  }

  return ParseFrameStatsOutputImpl(
      frame_stats_output_vector, local_start_timestamp_exclusive, data_vector);
}

int64_t GraphicsFrameStatsSampler::ParseFrameStatsOutputImpl(
    const std::vector<std::string>& frame_stats_lines,
    const int64_t start_timestamp_exclusive,
    std::vector<proto::GraphicsData>* data_vector) {
  Trace trace("GRAPHICS:ParseFrameStats");
  int64_t local_start_timestamp_exclusive = start_timestamp_exclusive;

  for (auto frame_stats_line : frame_stats_lines) {
    int64_t app_draw_timestamp = 0, vsync_timestamp = 0, set_timestamp = 0;

    auto tokens = Split(frame_stats_line, '\t');
    if (tokens.size() < 3) {
      continue;
    }

    app_draw_timestamp = atoll(tokens[0].c_str());
    vsync_timestamp = atoll(tokens[1].c_str());
    set_timestamp = atoll(tokens[2].c_str());

    // If a frame is partially rendered the frame will display as INT64_MAX.
    // This will always be the last frame in the set so break.
    if (app_draw_timestamp == INT64_MAX || vsync_timestamp == INT64_MAX ||
        set_timestamp == INT64_MAX) {
      break;
    }

    // Skip frames:
    // 1 - less than the start timestamp.
    // 2 - that have a value of 0 as those are padded values returned by
    // the command where no frames have yet been rendered.
    if (app_draw_timestamp <= local_start_timestamp_exclusive ||
        app_draw_timestamp == 0) {
      continue;
    }

    local_start_timestamp_exclusive = app_draw_timestamp;

    proto::GraphicsData sample;
    sample.mutable_frame_stats()->set_app_draw_timestamp(app_draw_timestamp);
    sample.mutable_frame_stats()->set_vsync_timestamp(vsync_timestamp);
    sample.mutable_frame_stats()->set_set_timestamp(set_timestamp);

    data_vector->push_back(sample);
  }

  return local_start_timestamp_exclusive;
}

std::vector<std::string> GraphicsFrameStatsSampler::Split(
    const std::string str, const char delimiter) {
  std::vector<std::string> internal;
  std::istringstream ss(str);
  std::string tok;

  while (getline(ss, tok, delimiter)) {
    internal.push_back(tok);
  }

  return internal;
}

}  // namespace profiler
