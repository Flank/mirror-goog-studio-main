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

#include "android_frame_timeline_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::AndroidFrameTimelineParameters
    AndroidFrameTimelineParameters;
typedef proto::AndroidFrameTimelineResult AndroidFrameTimelineResult;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/"
    "frame-timeline.trace");
const long IOSCHED_PROCESS_PID = 19645;
const long SURFACEFLINGER_PROCESS_PID = 678;

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

TEST(AndroidFrameTimelineRequestHandlerTest, PopulateFrameTimeline) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = AndroidFrameTimelineRequestHandler(tp.get());

  AndroidFrameTimelineParameters params_proto;
  params_proto.set_process_id(IOSCHED_PROCESS_PID);

  AndroidFrameTimelineResult result;
  handler.PopulateFrameTimeline(params_proto, &result);
  EXPECT_EQ(result.expected_slice_size(), 872);
  EXPECT_EQ(result.actual_slice_size(), 877);

  auto expected_slice = result.expected_slice(0);
  EXPECT_EQ(expected_slice.timestamp_nanoseconds(), 3624939299544L);
  EXPECT_EQ(expected_slice.duration_nanoseconds(), 16500000L);
  EXPECT_EQ(expected_slice.display_frame_token(), 274361L);
  EXPECT_EQ(expected_slice.surface_frame_token(), 274357L);
  EXPECT_EQ(expected_slice.layer_name(),
            "TX - com.google.samples.apps.iosched/"
            "com.google.samples.apps.iosched.ui.MainActivity#0");

  auto actual_slice = result.actual_slice(0);
  EXPECT_EQ(actual_slice.timestamp_nanoseconds(), 3624939299544L);
  EXPECT_EQ(actual_slice.duration_nanoseconds(), 7995887L);
  EXPECT_EQ(actual_slice.display_frame_token(), 274361L);
  EXPECT_EQ(actual_slice.surface_frame_token(), 274357L);
  EXPECT_EQ(actual_slice.layer_name(),
            "TX - com.google.samples.apps.iosched/"
            "com.google.samples.apps.iosched.ui.MainActivity#0");
  EXPECT_EQ(actual_slice.present_type(), "On-time Present");
  EXPECT_EQ(actual_slice.jank_type(), "None");
  EXPECT_EQ(actual_slice.on_time_finish(), true);
  EXPECT_EQ(actual_slice.gpu_composition(), false);
  EXPECT_EQ(actual_slice.layout_depth(), 0);

  // Verify overlapping slices have different layout_depth.
  EXPECT_EQ(result.actual_slice(99).layout_depth(), 1);
  EXPECT_EQ(result.actual_slice(414).layout_depth(), 2);
}

TEST(AndroidFrameTimelineRequestHandlerTest,
     PopulateFrameTimelineForSurfaceFlinger) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = AndroidFrameTimelineRequestHandler(tp.get());

  AndroidFrameTimelineParameters params_proto;
  params_proto.set_process_id(SURFACEFLINGER_PROCESS_PID);

  AndroidFrameTimelineResult result;
  handler.PopulateFrameTimeline(params_proto, &result);
  EXPECT_EQ(result.expected_slice_size(), 913);
  EXPECT_EQ(result.actual_slice_size(), 913);

  auto expected_slice = result.expected_slice(0);
  EXPECT_EQ(expected_slice.timestamp_nanoseconds(), 3624916605556L);
  EXPECT_EQ(expected_slice.duration_nanoseconds(), 10500051L);
  EXPECT_EQ(expected_slice.display_frame_token(), 274349L);
  EXPECT_EQ(expected_slice.surface_frame_token(), 0L);
  EXPECT_EQ(expected_slice.layer_name(), "");

  auto actual_slice = result.actual_slice(0);
  EXPECT_EQ(actual_slice.timestamp_nanoseconds(), 3624918340169L);
  EXPECT_EQ(actual_slice.duration_nanoseconds(), 8737031L);
  EXPECT_EQ(actual_slice.display_frame_token(), 274349L);
  EXPECT_EQ(actual_slice.surface_frame_token(), 0L);
  EXPECT_EQ(actual_slice.layer_name(), "");
  EXPECT_EQ(actual_slice.present_type(), "On-time Present");
  EXPECT_EQ(actual_slice.jank_type(), "None");
  EXPECT_EQ(actual_slice.on_time_finish(), true);
  EXPECT_EQ(actual_slice.gpu_composition(), false);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
