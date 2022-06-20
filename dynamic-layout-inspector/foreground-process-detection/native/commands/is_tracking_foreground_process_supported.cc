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

#include "is_tracking_foreground_process_supported.h"

#include "daemon/daemon.h"
#include "foreground_process_tracker.h"
#include "utils/log.h"

namespace layout_inspector {
grpc::Status IsTrackingForegroundProcessSupported::ExecuteOn(
    profiler::Daemon* daemon) {
  // Check if foreground process detection is supported
  TrackingForegroundProcessSupported::SupportType support_type =
      ForegroundProcessTracker::Instance(daemon->buffer())
          .IsTrackingForegroundProcessSupported();

  // Send the result to Studio.
  profiler::proto::Event event;
  event.set_kind(profiler::proto::Event::
                     LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED);
  auto* layoutInspectorForegroundProcessSupported =
      event.mutable_layout_inspector_tracking_foreground_process_supported();
  layoutInspectorForegroundProcessSupported->set_support_type(support_type);
  daemon->buffer()->Add(event);

  return grpc::Status::OK;
}

}  // namespace layout_inspector