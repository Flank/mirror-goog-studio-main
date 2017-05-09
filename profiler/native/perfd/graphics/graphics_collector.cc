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

namespace profiler {

GraphicsCollector::~GraphicsCollector() {
  if (is_running_) {
    Stop();
  }
}

void GraphicsCollector::Start() {}

void GraphicsCollector::Stop() {}

bool GraphicsCollector::IsRunning() { return is_running_.load(); }

void GraphicsCollector::Collect() {}

std::string GraphicsCollector::app_and_activity_name() {
  return app_and_activity_name_;
}

}  // namespace profiler
