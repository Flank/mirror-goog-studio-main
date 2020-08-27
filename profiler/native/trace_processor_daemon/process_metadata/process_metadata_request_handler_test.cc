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

#include "process_metadata_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::ProcessMetadataParameters
    ProcessMetadataParameters;
typedef proto::ProcessMetadataResult ProcessMetadataResult;

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

TEST(ProcessMetadataRequestHandlerTest, PopulateMetadataByProcessId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = ProcessMetadataRequestHandler(tp.get());

  ProcessMetadataParameters params_proto;
  params_proto.set_process_id(TANK_PROCESS_PID);

  ProcessMetadataResult result;
  handler.PopulateMetadata(params_proto, &result);

  EXPECT_EQ(result.process_size(), 1);
  EXPECT_EQ(result.dangling_thread_size(), 0);

  auto tank_process = result.process(0);
  EXPECT_EQ(tank_process.id(), TANK_PROCESS_PID);
  EXPECT_EQ(tank_process.internal_id(), 182);
  EXPECT_EQ(tank_process.name(), "com.google.android.tanks");
  EXPECT_EQ(tank_process.thread_size(), 63);
}

TEST(ProcessMetadataRequestHandlerTest, PopulateMetadataAllData) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = ProcessMetadataRequestHandler(tp.get());

  ProcessMetadataParameters params_proto;

  ProcessMetadataResult result;
  handler.PopulateMetadata(params_proto, &result);

  // tank.trace has 240 process, but we discard the process with pid = 0.
  EXPECT_EQ(result.process_size(), 239);
  EXPECT_EQ(result.dangling_thread_size(), 743);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
