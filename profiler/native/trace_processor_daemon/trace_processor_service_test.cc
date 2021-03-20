/*
 * Copyright (C) 2019 The Android Open Source Project
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

// Define this to NOP, in order to bypass custom code in our gtest
// that overrides the temp dir logic to work in Android devices.
// This doesn't work in Windows, as it can't find the constants for
// the access() call.
// See b/147797511#9 for context and more info.
// #define GTEST_INCLUDE_GTEST_INTERNAL_CUSTOM_GTEST_H_ ((void)0)

#include "trace_processor_service.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

namespace profiler {
namespace perfetto {
namespace {

const std::string TESTDATA_DIR(
    "tools/base/profiler/native/trace_processor_daemon/testdata/");

TEST(TraceProcessorServiceImplTest, LoadTraceNoTraceId) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest request;
  request.set_trace_path(TESTDATA_DIR + "tank.trace");

  proto::LoadTraceResponse response;

  const grpc::Status rs = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs.ok());
  EXPECT_FALSE(response.ok());
  EXPECT_EQ(response.error(), "Invalid Trace ID.");
}

TEST(TraceProcessorServiceImplTest, LoadTraceNoTracePath) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest request;
  request.set_trace_id(42);

  proto::LoadTraceResponse response;

  const grpc::Status rs = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs.ok());
  EXPECT_FALSE(response.ok());
  EXPECT_EQ(response.error(), "Empty Trace Path.");
}

TEST(TraceProcessorServiceImplTest, LoadTraceInvalidTracePath) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest request;
  request.set_trace_id(42);
  request.set_trace_path(TESTDATA_DIR + "missing.trace");

  proto::LoadTraceResponse response;

  const grpc::Status rs = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs.ok());
  EXPECT_FALSE(response.ok());
  EXPECT_EQ(response.error(), "Could not open trace file (path: " +
                                  TESTDATA_DIR + "missing.trace)");
}

// TODO(b/157742939): TP crashes 20~25% of time when loading a corrupted trace.
TEST(TraceProcessorServiceImplTest, DISABLED_LoadTraceCorruptedTrace) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest request;
  request.set_trace_id(42);
  request.set_trace_path(TESTDATA_DIR + "garbage.trace");

  proto::LoadTraceResponse response;

  const grpc::Status rs = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs.ok());
  EXPECT_FALSE(response.ok());
  EXPECT_EQ(response.error(),
            "Failed parsing a TracePacket from the partial buffer");
}

TEST(TraceProcessorServiceImplTest, LoadTrace) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest request;
  request.set_trace_id(42);
  request.set_trace_path(TESTDATA_DIR + "tank.trace");

  proto::LoadTraceResponse response;

  const grpc::Status rs = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs.ok());
  EXPECT_TRUE(response.ok());
  EXPECT_EQ(response.error(), "");

  // Let's do a "reload"
  // We set to missing, because since 42 is already loaded as tank.trace
  // it should return ok and keep that one.
  request.set_trace_id(42);
  request.set_trace_path(TESTDATA_DIR + "missing.trace");
  response.Clear();
  const grpc::Status rs_reload = svc.LoadTrace(nullptr, &request, &response);
  EXPECT_TRUE(rs_reload.ok());
  EXPECT_TRUE(response.ok());
  EXPECT_EQ(response.error(), "");
}

TEST(TraceProcessorServiceImplTest, BatchQuery) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest load_request;
  load_request.set_trace_id(7468186607525719778L);
  load_request.set_trace_path(TESTDATA_DIR + "tank.trace");

  proto::LoadTraceResponse load_response;
  svc.LoadTrace(nullptr, &load_request, &load_response);

  proto::QueryBatchRequest batch_request;
  auto query_params = batch_request.add_query();
  query_params->set_trace_id(7468186607525719778L);
  auto query = query_params->mutable_process_metadata_request();
  query->set_process_id(0);  // Returns all the info
  auto cpu_core_params = batch_request.add_query();
  cpu_core_params->set_trace_id(7468186607525719778L);
  cpu_core_params->mutable_cpu_core_counters_request();
  auto frame_events_params = batch_request.add_query();
  frame_events_params->set_trace_id(7468186607525719778L);
  frame_events_params->mutable_android_frame_events_request()
      ->set_layer_name_hint("foobar");

  proto::QueryBatchResponse batch_response;
  const grpc::Status rs =
      svc.QueryBatch(nullptr, &batch_request, &batch_response);
  EXPECT_TRUE(rs.ok());

  EXPECT_EQ(batch_response.result_size(), 3);

  // Result from the first query.
  auto process_metadata_result = batch_response.result(0);
  EXPECT_TRUE(process_metadata_result.ok());
  EXPECT_EQ(process_metadata_result.failure_reason(), proto::QueryResult::NONE);
  EXPECT_EQ(process_metadata_result.error(), "");
  EXPECT_TRUE(process_metadata_result.has_process_metadata_result());
  auto metadata = process_metadata_result.process_metadata_result();
  // tank.trace has 240 process, but we discard the process with pid = 0.
  EXPECT_EQ(metadata.process_size(), 239);

  // Result from the second query.
  auto cpu_core_counters_result = batch_response.result(1);
  EXPECT_TRUE(cpu_core_counters_result.ok());
  EXPECT_EQ(cpu_core_counters_result.failure_reason(),
            proto::QueryResult::NONE);
  EXPECT_EQ(cpu_core_counters_result.error(), "");
  EXPECT_TRUE(cpu_core_counters_result.has_cpu_core_counters_result());
  EXPECT_EQ(cpu_core_counters_result.cpu_core_counters_result().num_cores(), 8);

  // Result from the third query.
  auto android_frame_events_result = batch_response.result(2);
  EXPECT_TRUE(android_frame_events_result.ok());
  EXPECT_EQ(android_frame_events_result.failure_reason(),
            proto::QueryResult::NONE);
  EXPECT_EQ(android_frame_events_result.error(), "");
  EXPECT_TRUE(android_frame_events_result.has_android_frame_events_result());
}

TEST(TraceProcessorServiceImplTest, BatchQueryEmpty) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest load_request;
  load_request.set_trace_id(42);
  load_request.set_trace_path(TESTDATA_DIR + "tank.trace");

  proto::LoadTraceResponse load_response;
  svc.LoadTrace(nullptr, &load_request, &load_response);

  proto::QueryBatchRequest batch_request;
  proto::QueryBatchResponse batch_response;
  const grpc::Status rs =
      svc.QueryBatch(nullptr, &batch_request, &batch_response);
  EXPECT_TRUE(rs.ok());

  EXPECT_EQ(batch_response.result_size(), 0);
}

TEST(TraceProcessorServiceImplTest, BatchQueryNoLoadedTrace) {
  TraceProcessorServiceImpl svc;

  proto::QueryBatchRequest batch_request;
  auto query_params = batch_request.add_query();
  query_params->set_trace_id(0);
  auto query = query_params->mutable_process_metadata_request();
  query->set_process_id(0);  // Returns all the info

  proto::QueryBatchResponse batch_response;
  const grpc::Status rs =
      svc.QueryBatch(nullptr, &batch_request, &batch_response);
  EXPECT_TRUE(rs.ok());

  EXPECT_EQ(batch_response.result_size(), 1);
  auto result = batch_response.result(0);
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.failure_reason(), proto::QueryResult::TRACE_NOT_FOUND);
  EXPECT_EQ(result.error(), "No trace loaded.");
  EXPECT_FALSE(result.has_process_metadata_result());
}

TEST(TraceProcessorServiceImplTest, BatchQueryWrongLoadedTrace) {
  TraceProcessorServiceImpl svc;

  proto::LoadTraceRequest load_request;
  load_request.set_trace_id(42);
  load_request.set_trace_path(TESTDATA_DIR + "tank.trace");

  proto::LoadTraceResponse load_response;
  svc.LoadTrace(nullptr, &load_request, &load_response);

  proto::QueryBatchRequest batch_request;
  auto query_params = batch_request.add_query();
  query_params->set_trace_id(43);  // Different trace id.
  auto query = query_params->mutable_process_metadata_request();
  query->set_process_id(0);  // Returns all the info

  proto::QueryBatchResponse batch_response;
  const grpc::Status rs =
      svc.QueryBatch(nullptr, &batch_request, &batch_response);
  EXPECT_TRUE(rs.ok());

  EXPECT_EQ(batch_response.result_size(), 1);
  auto result = batch_response.result(0);
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.failure_reason(), proto::QueryResult::TRACE_NOT_FOUND);
  EXPECT_EQ(result.error(), "Unknown trace 43");
  EXPECT_FALSE(result.has_process_metadata_result());
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
