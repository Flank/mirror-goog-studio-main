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

#include "proto/graphics.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class GraphicsCollector {
 public:
  // Creates a collector that runs in the background continuously collecting
  // graphics data.
  // |app_and_activity_name| should be formatted as app name + "/" + activity
  // name.
  GraphicsCollector(const std::string &app_and_activity_name,
                    const Clock &clock)
      : app_and_activity_name_(app_and_activity_name) {}

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

 private:
  // Collects and saves Graphics sampling data continually.
  void Collect();

  // True if sampling operations is running.
  std::atomic_bool is_running_{false};
  // "app/activity" name combination
  std::string app_and_activity_name_;
};  // GraphicsCollector

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_COLLECTOR_H_
