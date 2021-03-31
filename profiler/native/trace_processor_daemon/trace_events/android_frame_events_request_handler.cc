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

#include "android_frame_events_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::AndroidFrameEventsResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::AndroidFrameEventsParameters
    AndroidFrameEventsParameters;

void AndroidFrameEventsRequestHandler::PopulateFrameEvents(
    AndroidFrameEventsParameters params, AndroidFrameEventsResult* result) {
  if (result == nullptr) {
    return;
  }

  // Query layers based on app process name.
  std::string layer_name_hint = params.layer_name_hint();
  if (layer_name_hint.empty()) {
    return;
  }
  // App layer name format: com.example.app/com.example.app.MainActivity#0
  auto layers = tp_->ExecuteQuery(
      "SELECT DISTINCT layer_name from frame_slice "
      "WHERE layer_name LIKE '" +
      layer_name_hint + "%'");
  while (layers.Next()) {
    auto layer_name = layers.Get(0).string_value;
    auto layer_proto = result->add_layer();
    layer_proto->set_layer_name(layer_name);
    PopulateFrameEventsByPhase(layer_name, "Display_*", "Display",
                               layer_proto->add_phase());
    PopulateFrameEventsByPhase(layer_name, "APP_*", "App",
                               layer_proto->add_phase());
    PopulateFrameEventsByPhase(layer_name, "GPU_*", "GPU",
                               layer_proto->add_phase());
    PopulateFrameEventsByPhase(layer_name, "SF_*", "Composition",
                               layer_proto->add_phase());
  }
}

void AndroidFrameEventsRequestHandler::PopulateFrameEventsByPhase(
    const std::string& layer_name, const std::string& phase_name_hint,
    const std::string& phase_name,
    proto::AndroidFrameEventsResult::Phase* phase_proto) {
  // Instead of frame_slice, query from experimiental_slice_layout, a SQL
  // function that condenses the slice table to minimize its vertical depth.
  // See
  // https://github.com/google/perfetto/blob/master/src/trace_processor/dynamic/experimental_slice_layout_generator.cc
  auto frame_events = tp_->ExecuteQuery(
      "SELECT id, ts, dur, cast(name AS INT) AS frame_number, "
      "  depth, layout_depth "
      "FROM experimental_slice_layout WHERE filter_track_ids = "
      "  (SELECT group_concat(track_id) FROM "
      "    (SELECT name, track_id FROM gpu_track INNER JOIN "
      "      (SELECT DISTINCT track_id FROM frame_slice "
      "       WHERE layer_name LIKE '" +
      layer_name +
      "') t ON gpu_track.id = t.track_id) "
      "     WHERE name GLOB '" +
      phase_name_hint + "') ORDER BY ts");
  while (frame_events.Next()) {
    auto frame_event_proto = phase_proto->add_frame_event();
    frame_event_proto->set_id(frame_events.Get(0).long_value);
    frame_event_proto->set_timestamp_nanoseconds(
        frame_events.Get(1).long_value);
    frame_event_proto->set_duration_nanoseconds(frame_events.Get(2).long_value);
    frame_event_proto->set_frame_number(frame_events.Get(3).long_value);
    if (phase_name.compare("Display") == 0) {
      // Just use depth for the Display phase as their slices never overlap
      // (slice1.end == slice2.start).
      frame_event_proto->set_depth(frame_events.Get(4).long_value);
    } else {
      // Other phases will use layout_depth, as calculated by
      // experimiental_slice_layout.
      frame_event_proto->set_depth(frame_events.Get(5).long_value);
    }
  }
  phase_proto->set_phase_name(phase_name);
}
