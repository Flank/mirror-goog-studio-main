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

#include "trace_metadata_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::TraceMetadataParameters
    TraceMetadataParameters;
typedef proto::TraceMetadataResult TraceMetadataResult;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/tank.trace");

const long TANK_PROCESS_PID = 9796;

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}


TEST(TraceMetadataRequestHandlerTest, PopulateMetadataAllData) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = TraceMetadataRequestHandler(tp.get());

  TraceMetadataParameters params;

  TraceMetadataResult result;
  handler.PopulateTraceMetadata(params, &result);

  // tank.trace has 7 rows of metadata
  EXPECT_EQ(result.metadata_row_size(), 7);
}

TEST(TraceMetadataRequestHandlerTest, PopulateMetadataByName) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = TraceMetadataRequestHandler(tp.get());

  TraceMetadataParameters params;
  params.set_name("system_machine");

  TraceMetadataResult result;
  handler.PopulateTraceMetadata(params, &result);

  EXPECT_EQ(result.metadata_row_size(), 1);
  EXPECT_EQ(result.metadata_row().Get(0).string_value(), "aarch64");
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
