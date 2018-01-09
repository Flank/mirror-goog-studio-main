/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "perfd/cpu/simpleperf.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <cstring>
#include <memory>

using std::string;
using testing::IsNull;
using testing::Not;
using testing::StartsWith;
using testing::StrEq;

namespace {

const char* const kFakeSimpleperfDir = "/fake/path/";
const char* const kFakeTracePath = "/tmp/fake-trace";

// Checks that |a1| is a legitimate command line argument. It should appear in
// the given input string at least once; and for each occurrence, it should not
// start the string, should follow a space, and should be followed by a space
// unless it ends the string.
MATCHER_P(HasArgument, a1, "") {
  string candidate{a1};
  if (candidate.length() == 0) return true;

  std::string::size_type position = 0;
  int occurrence = 0;
  while (true) {
    position = arg.find(candidate, position);
    if (position == std::string::npos) {
      break;
    } else {
      occurrence++;
      // Check an argument should not be at the beginning of the string.
      if (position == 0) {
        *result_listener << "\"" << a1 << "\" should not start the string";
        return false;
      } else {
        // Check an argument should follow a space.
        if (arg[position - 1] != ' ') {
          *result_listener << "\"" << a1 << "\" should follow a space";
          return false;
        }
        // Update position to points to the first character after the match.
        position = position + candidate.length();
        if (position < arg.length() && arg[position] != ' ') {
          *result_listener
              << "\"" << a1
              << "\" should end the string or be followed by a space";
          return false;
        }
      }
    }
  }
  if (occurrence == 0) {
    *result_listener << "\"" << a1 << "\" should appear at least once";
    return false;
  }
  return true;
}

}  // namespace

namespace profiler {

// A subclass of Simpleperf to be used in tests. This class essentially
// overrides |GetFeatures| so we can simulate a call to `simpleperf list
// --show-features`, which affects the result of |GetRecordCommand|.
class FakeSimpleperfGetFeatures final : public Simpleperf {
 public:
  explicit FakeSimpleperfGetFeatures(bool is_emulator)
      : Simpleperf(kFakeSimpleperfDir, is_emulator) {}

  // A public wrapper for the homonym protected method, for testing.
  string GetRecordCommand(int pid, const string& pkg_name,
                          const string& abi_arch, const string& trace_path,
                          int sampling_interval_us) const {
    return Simpleperf::GetRecordCommand(pid, pkg_name, abi_arch, trace_path,
                                        sampling_interval_us);
  }

  // A public wrapper for the homonym protected method, for testing.
  void SplitRecordCommand(char* original_cmd, char** split_cmd) const {
    return Simpleperf::SplitRecordCommand(original_cmd, split_cmd);
  }

  string GetFeatures(const string& abi_arch) const { return features_; }

  void SetFeatures(string features) { features_ = features; }

 private:
  string features_;
};

TEST(SimpleperfTest, RecordCommandParams) {
  FakeSimpleperfGetFeatures simpleperf{false};

  string record_command = simpleperf.GetRecordCommand(3039, "my.package", "arm",
                                                      kFakeTracePath, 100);

  // simpleperf binary + "record"
  EXPECT_THAT(record_command, StartsWith("/fake/path/simpleperf_arm record"));
  // PID
  EXPECT_THAT(record_command, HasArgument("-p 3039"));
  // package name
  EXPECT_THAT(record_command, HasArgument("--app my.package"));
  // trace path
  EXPECT_THAT(record_command, HasArgument("-o /tmp/fake-trace"));
  // Sampling frequency. Note sampling interval is 100us, so frequency is 10000
  // samples per second.
  EXPECT_THAT(record_command, HasArgument("-f 10000"));
  // --exit-with-parent flag
  EXPECT_THAT(record_command, HasArgument("--exit-with-parent"));
}

TEST(SimpleperfTest, SimpleperfBinaryName) {
  FakeSimpleperfGetFeatures simpleperf{false};
  int pid = 42;
  string app = "my.good.app";
  int sampling_interval = 100;

  string record_command = simpleperf.GetRecordCommand(
      pid, app, "arm", kFakeTracePath, sampling_interval);
  EXPECT_THAT(record_command, StartsWith("/fake/path/simpleperf_arm record"));

  record_command = simpleperf.GetRecordCommand(
      pid, app, "arm64", kFakeTracePath, sampling_interval);
  EXPECT_THAT(record_command, StartsWith("/fake/path/simpleperf_arm64 record"));

  record_command = simpleperf.GetRecordCommand(pid, app, "x86", kFakeTracePath,
                                               sampling_interval);
  EXPECT_THAT(record_command, StartsWith("/fake/path/simpleperf_x86 record"));

  record_command = simpleperf.GetRecordCommand(
      pid, app, "x86_64", kFakeTracePath, sampling_interval);
  EXPECT_THAT(record_command,
              StartsWith("/fake/path/simpleperf_x86_64 record"));
}

TEST(SimpleperfTest, EmulatorUsesCpuClockEvents) {
  FakeSimpleperfGetFeatures simpleperf_emulator{true /* is_emulator */};
  string record_command = simpleperf_emulator.GetRecordCommand(
      1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, HasArgument("-e cpu-clock"));

  FakeSimpleperfGetFeatures simpleperf{false /* is_emulator */};
  record_command =
      simpleperf.GetRecordCommand(1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, Not(HasArgument("-e cpu-clock")));
}

TEST(SimpleperfTest, TraceOffCpuFlag) {
  FakeSimpleperfGetFeatures simpleperf{false};
  simpleperf.SetFeatures("trace-offcpu\nother feature");
  string record_command =
      simpleperf.GetRecordCommand(1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, HasArgument("--trace-offcpu"));

  simpleperf.SetFeatures("other feature");
  record_command =
      simpleperf.GetRecordCommand(1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, Not(HasArgument("--trace-offcpu")));
}

TEST(SimpleperfTest, DwarfVsFpCallGraph) {
  FakeSimpleperfGetFeatures simpleperf{false};
  simpleperf.SetFeatures("dwarf-based-call-graph");
  string record_command =
      simpleperf.GetRecordCommand(1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, HasArgument("--call-graph dwarf"));

  simpleperf.SetFeatures("");
  record_command =
      simpleperf.GetRecordCommand(1, "any.package", "arm", kFakeTracePath, 1);
  EXPECT_THAT(record_command, HasArgument("--call-graph fp"));
}

TEST(SimpleperfTest, SplitRecordCommand) {
  FakeSimpleperfGetFeatures simpleperf{false};
  char* split_str[5];
  std::unique_ptr<char[]> original_str;

  string test_string = "";  // Empty string
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], IsNull());

  test_string = " ";  // Single space
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], IsNull());

  test_string = "String with spaces";
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], StrEq("String"));
  EXPECT_THAT(split_str[1], StrEq("with"));
  EXPECT_THAT(split_str[2], StrEq("spaces"));
  EXPECT_THAT(split_str[3], IsNull());

  test_string = "Other string with\0null character";
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], StrEq("Other"));
  EXPECT_THAT(split_str[1], StrEq("string"));
  EXPECT_THAT(split_str[2], StrEq("with"));
  EXPECT_THAT(split_str[3], testing::IsNull());

  test_string = " leading space";
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], StrEq("leading"));
  EXPECT_THAT(split_str[1], StrEq("space"));
  EXPECT_THAT(split_str[2], testing::IsNull());

  test_string = "trailing space ";
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], StrEq("trailing"));
  EXPECT_THAT(split_str[1], StrEq("space"));
  EXPECT_THAT(split_str[2], testing::IsNull());

  test_string = "double  space";
  original_str.reset(new char[test_string.length() + 1]);
  std::strcpy(original_str.get(), test_string.c_str());
  simpleperf.SplitRecordCommand(original_str.get(), split_str);
  EXPECT_THAT(split_str[0], StrEq("double"));
  EXPECT_THAT(split_str[1], StrEq("space"));
  EXPECT_THAT(split_str[2], testing::IsNull());
}

}  // namespace profiler
