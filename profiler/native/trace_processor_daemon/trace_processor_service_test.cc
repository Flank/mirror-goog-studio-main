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

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "trace_processor_service.h"
#include "trace_processor_service.pb.h"

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

  // tank.trace has 240 process, but we discard the process with pid = 0.
  EXPECT_EQ(response.process_metadata().process_size(), 239);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
