/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "android_frame_timeline_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::AndroidFrameTimelineResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::AndroidFrameTimelineParameters
    AndroidFrameTimelineParameters;

void AndroidFrameTimelineRequestHandler::PopulateFrameTimeline(
    AndroidFrameTimelineParameters params, AndroidFrameTimelineResult* result) {
  if (result == nullptr) {
    return;
  }

  // Expected timeline.
  auto expected_timeline = tp_->ExecuteQuery(
      "SELECT ts, dur, display_frame_token, surface_frame_token, layer_name "
      "FROM (SELECT t.*, process_track.name as track_name "
      "      FROM process_track LEFT JOIN expected_frame_timeline_slice t "
      "      ON process_track.id = t.track_id) s "
      "JOIN process USING(upid) "
      "WHERE s.track_name = 'Expected Timeline' AND process.pid = " +
      std::to_string(params.process_id()) + " ORDER BY ts");
  while (expected_timeline.Next()) {
    auto expected_slice = result->add_expected_slice();
    expected_slice->set_timestamp_nanoseconds(
        expected_timeline.Get(0).long_value);
    expected_slice->set_duration_nanoseconds(
        expected_timeline.Get(1).long_value);
    expected_slice->set_display_frame_token(
        expected_timeline.Get(2).long_value);
    expected_slice->set_surface_frame_token(
        expected_timeline.Get(3).long_value);
    // The surfaceflinger process doesn't have a layer_name.
    if (!expected_timeline.Get(4).is_null()) {
      expected_slice->set_layer_name(expected_timeline.Get(4).string_value);
    }
  }

  // Actual timeline.
  auto actual_timeline = tp_->ExecuteQuery(
      "SELECT ts, dur, display_frame_token, surface_frame_token, layer_name, "
      "       present_type, jank_type, on_time_finish, gpu_composition "
      "FROM (SELECT t.*, process_track.name as track_name "
      "      FROM process_track LEFT JOIN actual_frame_timeline_slice t "
      "      ON process_track.id = t.track_id) s "
      "JOIN process USING(upid) "
      "WHERE s.track_name = 'Actual Timeline' AND process.pid = " +
      std::to_string(params.process_id()) + " ORDER BY ts");
  while (actual_timeline.Next()) {
    auto actual_slice = result->add_actual_slice();
    actual_slice->set_timestamp_nanoseconds(actual_timeline.Get(0).long_value);
    actual_slice->set_duration_nanoseconds(actual_timeline.Get(1).long_value);
    actual_slice->set_display_frame_token(actual_timeline.Get(2).long_value);
    actual_slice->set_surface_frame_token(actual_timeline.Get(3).long_value);
    // The surfaceflinger process doesn't have a layer_name.
    if (!actual_timeline.Get(4).is_null()) {
      actual_slice->set_layer_name(actual_timeline.Get(4).string_value);
    }
    actual_slice->set_present_type(actual_timeline.Get(5).string_value);
    actual_slice->set_jank_type(actual_timeline.Get(6).string_value);
    actual_slice->set_on_time_finish(actual_timeline.Get(7).long_value);
    actual_slice->set_gpu_composition(actual_timeline.Get(8).long_value);
  }
}
