/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "stop_tracking_foreground_process.h"

#include "daemon/daemon.h"
#include "foreground_process_tracker.h"

namespace layout_inspector {
grpc::Status StopTrackingForegroundProcess::ExecuteOn(
    profiler::Daemon* daemon) {
  ForegroundProcessTracker::Instance(daemon->buffer()).StopTracking();
  return grpc::Status::OK;
}

}  // namespace layout_inspector