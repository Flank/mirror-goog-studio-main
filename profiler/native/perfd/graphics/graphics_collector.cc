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
#include "graphics_collector.h"

#include <unistd.h>
#include <sstream>

#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

using profiler::proto::GraphicsData;

namespace profiler {

GraphicsCollector::~GraphicsCollector() {
  if (is_running_) {
    Stop();
  }
}

void GraphicsCollector::Start() {
  if (!is_running_.exchange(true)) {
    sampler_thread_ = std::thread(&GraphicsCollector::Collect, this);
  }
}

void GraphicsCollector::Stop() {
  if (is_running_.exchange(false)) {
    sampler_thread_.join();
  }
}

bool GraphicsCollector::IsRunning() { return is_running_.load(); }

void GraphicsCollector::Collect() {
  SetThreadName("Studio:PollGrap");

  Stopwatch stopwatch;
  int64_t start_timestamp_exclusive = INT64_MIN;
  while (is_running_) {
    Trace::Begin("GRAPHICS:Collect");
    int64_t start_time_ns = stopwatch.GetElapsed();

    std::string str_get_dumpsys{GraphicsFrameStatsSampler::GetDumpsysCommand()};
    if (!str_get_dumpsys.empty()) {
      BashCommandRunner cmd_get_dumpsys{str_get_dumpsys};

      std::vector<GraphicsData> data_vector;
      // For each sampler call we will get multiple GraphicsData
      start_timestamp_exclusive =
          graphics_frame_stats_sampler_.GetFrameStatsVector(
              start_timestamp_exclusive, cmd_get_dumpsys, &data_vector);

      graphics_cache_.SaveGraphicsDataVector(data_vector);
    }
    Trace::End();
    int64_t elapsed_time_ns = stopwatch.GetElapsed() - start_time_ns;
    if (kSleepNs > elapsed_time_ns) {
      int64_t sleep_time_us = Clock::ns_to_us(kSleepNs - elapsed_time_ns);
      usleep(static_cast<uint64_t>(sleep_time_us));
    }
  }
  is_running_.exchange(false);
}

}  // namespace profiler
