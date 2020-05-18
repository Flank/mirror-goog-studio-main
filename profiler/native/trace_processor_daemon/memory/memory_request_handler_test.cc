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

#include "memory_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "absl/strings/escaping.h"
#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"
#include "trace_processor_service.h"

namespace profiler {
namespace perfetto {
namespace {

using ::perfetto::trace_processor::Config;
using ::perfetto::trace_processor::ReadTrace;
using ::perfetto::trace_processor::TraceProcessor;
using proto::NativeAllocationContext;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/"
    "unity.heapprofd");

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

TEST(MemoryRequestHandlerTest, TestBase64Encoded) {
  auto tp = LoadTrace(TESTDATA_PATH);
  MemoryRequestHandler handler{tp.get()};
  NativeAllocationContext context;
  handler.PopulateEvents(&context);
  std::string dest;

  EXPECT_TRUE(absl::Base64Unescape(context.frames(0).name(), &dest));
#ifndef _MSC_VER  // Demangling is not currently available on windows.
  // Validate frame names are demanged
  EXPECT_NE(dest.rfind("_Z", 0), 0);
#else
  // Until b/151081845 is fixed validate name is set.
  EXPECT_EQ(dest.rfind("_Z", 0), 0);
#endif
  EXPECT_TRUE(absl::Base64Unescape(context.frames(0).module(), &dest));
}

TEST(MemoryRequestHandlerTest, TestMemoryDataPopulated) {
  auto tp = LoadTrace(TESTDATA_PATH);
  MemoryRequestHandler handler{tp.get()};
  NativeAllocationContext context;
  handler.PopulateEvents(&context);
  EXPECT_EQ(context.allocations_size(), 473);
  EXPECT_EQ(context.pointers_size(), 1484);
  EXPECT_EQ(context.frames_size(), 599);

  // Validate allocations point to a valid stack
  long long stack_id = context.allocations(0).stack_id();
  EXPECT_LT(stack_id, context.pointers_size());
  // Validate stack points to a valid frame
  long frame_id = context.pointers().at(stack_id).frame_id();
  EXPECT_NE(frame_id, 0);
  EXPECT_LT(frame_id, context.frames_size());
  // Validate frame has a name
  EXPECT_STRNE(context.frames(frame_id).name().c_str(), "");
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
