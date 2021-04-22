/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::AndroidFrameEventsParameters
    AndroidFrameEventsParameters;
typedef proto::AndroidFrameEventsResult AndroidFrameEventsResult;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/frame.trace");

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

TEST(AndroidFrameEventsRequestHandlerTest, PopulateEventsByLayerName) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = AndroidFrameEventsRequestHandler(tp.get());

  AndroidFrameEventsParameters params_proto;
  params_proto.set_layer_name_hint("android.com.java.profilertester");

  AndroidFrameEventsResult result;
  handler.PopulateFrameEvents(params_proto, &result);
  EXPECT_EQ(result.layer_size(), 1);

  auto layer = result.layer(0);
  EXPECT_EQ(layer.layer_name(),
            "android.com.java.profilertester/"
            "android.com.java.profilertester.MainActivity#0");
  EXPECT_EQ(layer.phase_size(), 4);

  auto display_phase = layer.phase(0);
  EXPECT_EQ(display_phase.phase_name(), "Display");
  EXPECT_EQ(display_phase.frame_event_size(), 428);
  auto display_event = display_phase.frame_event(0);
  EXPECT_EQ(display_event.id(), 958);
  EXPECT_EQ(display_event.timestamp_nanoseconds(), 2671654879872917L);
  EXPECT_EQ(display_event.duration_nanoseconds(), 22447919L);
  EXPECT_EQ(display_event.frame_number(), 4);
  EXPECT_EQ(display_event.depth(), 0);
  display_event = display_phase.frame_event(427);
  EXPECT_EQ(display_event.id(), 123053);
  EXPECT_EQ(display_event.timestamp_nanoseconds(), 2671665780586815L);
  EXPECT_EQ(display_event.duration_nanoseconds(), -1L);
  EXPECT_EQ(display_event.frame_number(), 432);
  EXPECT_EQ(display_event.depth(), 0);

  auto app_phase = layer.phase(1);
  EXPECT_EQ(app_phase.phase_name(), "App");
  EXPECT_EQ(app_phase.frame_event_size(), 428);
  auto app_event = app_phase.frame_event(0);
  EXPECT_EQ(app_event.id(), 646);
  EXPECT_EQ(app_event.timestamp_nanoseconds(), 2671654858568696L);
  EXPECT_EQ(app_event.duration_nanoseconds(), 3737188L);
  EXPECT_EQ(app_event.frame_number(), 4);
  EXPECT_EQ(app_event.depth(), 0);
  app_event = app_phase.frame_event(427);
  EXPECT_EQ(app_event.id(), 123137);
  EXPECT_EQ(app_event.timestamp_nanoseconds(), 2671665783520253L);
  EXPECT_EQ(app_event.duration_nanoseconds(), 1468542L);
  EXPECT_EQ(app_event.frame_number(), 433);
  EXPECT_EQ(app_event.depth(), 0);

  auto gpu_phase = layer.phase(2);
  EXPECT_EQ(gpu_phase.phase_name(), "GPU");
  EXPECT_EQ(gpu_phase.frame_event_size(), 424);
  auto gpu_event = gpu_phase.frame_event(0);
  EXPECT_EQ(gpu_event.id(), 704);
  EXPECT_EQ(gpu_event.timestamp_nanoseconds(), 2671654862305884L);
  EXPECT_EQ(gpu_event.duration_nanoseconds(), 1130885L);
  EXPECT_EQ(gpu_event.frame_number(), 4);
  EXPECT_EQ(gpu_event.depth(), 0);
  gpu_event = gpu_phase.frame_event(423);
  EXPECT_EQ(gpu_event.id(), 123220);
  EXPECT_EQ(gpu_event.timestamp_nanoseconds(), 2671665784988795L);
  EXPECT_EQ(gpu_event.duration_nanoseconds(), 1126979L);
  EXPECT_EQ(gpu_event.frame_number(), 433);
  EXPECT_EQ(gpu_event.depth(), 0);

  auto composition_phase = layer.phase(3);
  EXPECT_EQ(composition_phase.phase_name(), "Composition");
  EXPECT_EQ(composition_phase.frame_event_size(), 430);
  auto composition_event = composition_phase.frame_event(0);
  EXPECT_EQ(composition_event.id(), 747);
  EXPECT_EQ(composition_event.timestamp_nanoseconds(), 2671654869373697L);
  EXPECT_EQ(composition_event.duration_nanoseconds(), 10499220L);
  EXPECT_EQ(composition_event.frame_number(), 4);
  EXPECT_EQ(composition_event.depth(), 0);
  composition_event = composition_phase.frame_event(428);
  EXPECT_EQ(composition_event.id(), 122840);
  EXPECT_EQ(composition_event.timestamp_nanoseconds(), 2671665770393272L);
  EXPECT_EQ(composition_event.duration_nanoseconds(), 10193543L);
  EXPECT_EQ(composition_event.frame_number(), 432);
  EXPECT_EQ(composition_event.depth(), 1);
}

TEST(AndroidFrameEventsRequestHandlerTest, PopulateEventsEmptyLayerName) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = AndroidFrameEventsRequestHandler(tp.get());

  AndroidFrameEventsParameters params_proto;
  AndroidFrameEventsResult result;
  handler.PopulateFrameEvents(params_proto, &result);
  EXPECT_EQ(result.layer_size(), 0);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
