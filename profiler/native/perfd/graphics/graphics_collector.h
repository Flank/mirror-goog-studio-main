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
#ifndef PERFD_GRAPHICS_GRAPHICS_COLLECTOR_H_
#define PERFD_GRAPHICS_GRAPHICS_COLLECTOR_H_

#include <atomic>
#include <cstdint>
#include <string>
#include <thread>

#include "perfd/graphics/graphics_cache.h"
#include "perfd/graphics/graphics_framestats_sampler.h"
#include "proto/graphics.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class GraphicsCollector {
 private:
  // Default collection interval is 250 milliseconds, i.e., 0.25 second.
  static constexpr int64_t kSleepNs = Clock::ms_to_ns(250);
  // Buffer for 10 seconds to prevent lost frames.
  static const int64_t kSecondsToBuffer = 10;
  // There can be at most ~60 frames in a second.
  static constexpr int64_t kSamplesCount = kSecondsToBuffer * 60;

 public:
  // Creates a collector that runs in the background collecting graphics data
  // every |kSleepNs| nanoseconds.
  // |app_and_activity_name| should be formatted as app name + "/" + activity
  // name.
  GraphicsCollector(const std::string &app_and_activity_name,
                    const Clock &clock)
      : graphics_cache_(clock, kSamplesCount),
        app_and_activity_name_(app_and_activity_name) {}

  ~GraphicsCollector();

  bool IsRunning();

  // Creates a thread that collects and saves data continually.
  // Assumes |Start()| and |Stop()| are called by the same thread.
  void Start();

  // Stops collecting data and wait for thread exit.
  // Assumes |Start()| and |Stop()| are called by the same thread.
  void Stop();

  // Return the app and activity string this graphics collector will monitor.
  std::string app_and_activity_name();

  GraphicsCache &graphics_cache() { return graphics_cache_; }

 private:
  // Collects and saves Graphics sampling data continually.
  void Collect();

  // Cache where collected data will be saved.
  GraphicsCache graphics_cache_;
  // Thread that sampling operations run on.
  std::thread sampler_thread_;
  // Holder of sampler operations.
  GraphicsFrameStatsSampler graphics_frame_stats_sampler_;
  // True if sampling operations is running.
  std::atomic_bool is_running_{false};
  // "app/activity" name combination
  std::string app_and_activity_name_;
};  // GraphicsCollector

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_COLLECTOR_H_
