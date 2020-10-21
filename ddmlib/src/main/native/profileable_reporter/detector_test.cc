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
#include "ddmlib/src/main/native/profileable_reporter/detector.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <map>
#include <regex>
#include <sstream>

#include "transport/native/utils/fs/memory_file_system.h"

using profiler::FileSystem;
using profiler::MemoryFileSystem;
using std::map;
using std::ostringstream;
using std::string;
using std::unique_ptr;
using testing::_;
using testing::Eq;
using testing::HasSubstr;
using testing::Ne;
using testing::Not;
using testing::Return;
using testing::SaveArg;
using testing::StartsWith;
using testing::StrEq;
using testing::Unused;

namespace {

class MockProfileableChecker final : public ddmlib::ProfileableChecker {
 public:
  MOCK_CONST_METHOD2(Check, bool(int32_t pid, const std::string& package_name));
};

}  // namespace

namespace ddmlib {

struct ParsedOutput {
  int32_t process_count;
  int32_t app_count;
  map<int32_t, ProcessInfo> profileables;  // map from pid to the process info
};

class DetectorTest : public ::testing::Test {
 public:
  int32_t kZygote64Pid = 11;
  int32_t kZygotePid = 12;

  DetectorTest()
      : detector_(
            Detector::LogFormat::kDebug,
            unique_ptr<FileSystem>(new MemoryFileSystem()),
            unique_ptr<ProfileableChecker>(new MockProfileableChecker())) {
    SetupZygoteFiles();
  }

  void AddProcessFiles(int32_t pid, const string& name, int32_t ppid,
                       int64_t start_time) {
    AddCmdlineFile(pid, name);
    AddStatFile(pid, name, ppid, start_time);
  }

  string RefreshAndRetrieveLog() {
    ostringstream oss;
    detector_.Refresh(oss);
    return oss.str();
  }

  ParsedOutput ParseLog(const string& log) {
    ParsedOutput output;
    bool parse_result = ParseLogFormat2(log, &output);
    EXPECT_TRUE(parse_result);
    return output;
  }

  void AddCmdlineFile(int32_t pid, const string& cmdline) {
    AddFile(detector_.proc_files()->GetProcessCmdlineFilePath(pid), cmdline);
  }

  void AddStatFile(int32_t pid, const string& name, int32_t ppid,
                   int64_t start_time) {
    char content[1024];
    sprintf(content,
            "%d (%s) S %d 123 0 0 -1 1077936448 164229 0 231 0 2437 5139 0 0 "
            "20 0 57 0 %lld 1441751040 46123 18446744073709551615 1 1 0 0 0 0 "
            "4612 1 "
            "1073775864 0 0 0 17 2 0 0 0 0 0 0 0 0 0 0 0 0 0",
            pid, name.c_str(), ppid, start_time);
    AddFile(detector_.proc_files()->GetProcessStatFilePath(pid), content);
  }

 protected:
  // Mark it protected so it's accessible for tests in this file.
  Detector detector_;

 private:
  static bool ParseLogFormat2(const string& log, ParsedOutput* output) {
    std::cmatch cm;
    std::basic_regex<char> re(
        "(([0-9]+) profileable processes\\n((.+\\n)*))?    Query takes [0-9]+ "
        "ms \\(([0-9]+) processes, ([0-9]+) apps\\)\n");

    if (output == nullptr) return false;
    // Explicitly mark the output as invalid.
    output->process_count = -1;
    output->app_count = -1;
    output->profileables.clear();

    if (std::regex_match(log.c_str(), cm, re)) {
      EXPECT_EQ(cm.size(), 7);
      // cm[0] is the entire string. Ignore.
      // cm[1] is everything before "   Query takes...". Ignore.
      int profileable_count = atoi(cm[2].str().c_str());
      // cm[3] includes all profileable processes, one on each line
      if (!ParseProfileableLines(cm[3].str(), &output->profileables)) {
        return false;
      }
      EXPECT_EQ(profileable_count, output->profileables.size());
      // cm[4] is the last line of profileable processes. Ignore.
      output->process_count = atoi(cm[5].str().c_str());
      output->app_count = atoi(cm[6].str().c_str());
      return true;
    }
    EXPECT_TRUE(false);  // Input doesn't match the expected regex
    return false;
  }

  // Parse zero or more lines like "123 com.app1 start_time: 2345\n"
  static bool ParseProfileableLines(const string& log,
                                    map<int32_t, ProcessInfo>* output) {
    int start = 0;
    int found = 0;
    while ((found = log.find('\n', start)) != std::string::npos) {
      const string& line = log.substr(start, found - start);
      std::cmatch cm;
      std::basic_regex<char> re("([0-9]+) (.*) start_time: ([0-9]+)");

      if (std::regex_match(line.c_str(), cm, re)) {
        ProcessInfo info;
        info.pid = atoi(cm[1].str().c_str());
        info.package_name = cm[2].str();
        info.start_time = atoll(cm[3].str().c_str());
        info.profileable = true;
        (*output)[info.pid] = info;
      } else {
        return false;
      }
      start = found + 1;
    }
    return true;
  }

  void AddFile(const string& path, const string& content) {
    auto file = detector_.file_system()->NewFile(path);
    file->OpenForWrite();
    file->Append(content);
    file->Close();
  }

  void SetupZygoteFiles() {
    AddCmdlineFile(kZygote64Pid, "zygote64\0   ignored characters");
    AddCmdlineFile(kZygotePid, "zygote");
  }
};

TEST_F(DetectorTest, LogOnStartupForNoProfileableApp) {
  // Default log format in this test file is LogFormat::kDebug.
  string log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, StartsWith("0 profileable processes\n"));
}

TEST_F(DetectorTest, LogFormatShouldControlQueryStats) {
  // Default log format in this test file is LogFormat::kDebug.
  string log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, HasSubstr("Query takes"));

  detector_.SetLogFormat(Detector::LogFormat::kHuman);
  log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, Not(HasSubstr("Query takes")));
}

TEST_F(DetectorTest, LogFormatShouldControlStartTime) {
  // Default log format in this test file is LogFormat::kDebug.
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(Return(true));
  string log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, HasSubstr("start_time:"));

  detector_.SetLogFormat(Detector::LogFormat::kHuman);
  // Add another profileable process to force logging
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);
  log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, Not(HasSubstr("start_time:")));
}

TEST_F(DetectorTest, Find32bitProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  int32_t checked_pid;
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                      Return(true)));

  auto output = ParseLog(RefreshAndRetrieveLog());
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.app1"));

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 1);
  ASSERT_THAT(output.profileables.size(), 1);
  const auto& found = output.profileables.find(123);
  EXPECT_THAT(found->second.package_name, StrEq("com.app1"));
  EXPECT_THAT(found->second.start_time, 2345);
}

// The same as Find32bitProfileable but the app process is 64 bit.
TEST_F(DetectorTest, Find64bitProfileable) {
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);

  int32_t checked_pid;
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                      Return(true)));

  auto output = ParseLog(RefreshAndRetrieveLog());
  EXPECT_THAT(checked_pid, 456);
  EXPECT_THAT(checked_name, StrEq("com.app2"));

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 1);
  ASSERT_THAT(output.profileables.size(), 1);
  const auto& found = output.profileables.find(456);
  EXPECT_THAT(found->second.package_name, StrEq("com.app2"));
  EXPECT_THAT(found->second.start_time, 4321);
}

TEST_F(DetectorTest, FindTwoProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true))
      .WillOnce(Return(true));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 4);
  EXPECT_THAT(output.app_count, 2);
  ASSERT_THAT(output.profileables.size(), 2);
  const auto& found_1 = output.profileables.find(123);
  EXPECT_THAT(found_1->second.package_name, StrEq("com.app1"));
  const auto& found_2 = output.profileables.find(456);
  EXPECT_THAT(found_2->second.package_name, StrEq("com.app2"));
}

TEST_F(DetectorTest, FindOneProfileableOneNonProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true))
      .WillOnce(Return(false));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 4);
  EXPECT_THAT(output.app_count, 2);
  EXPECT_THAT(output.profileables.size(), 1);
}

TEST_F(DetectorTest, DontCheckNonAppProcess) {
  AddProcessFiles(123, "NotAnApp", 11111 /* not zygote */, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(0);  // Shouldn't be called

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 0);
  EXPECT_THAT(output.profileables.size(), 0);
}

TEST_F(DetectorTest, DontCheckSameProfileableAppAgain) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(true));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 1);
  EXPECT_THAT(output.profileables.size(), 1);

  // The subsequent refreshes shouldn't call the Check() function.
  ostringstream oss;
  detector_.Refresh(oss);
  detector_.Refresh(oss);
  detector_.Refresh(oss);
}

TEST_F(DetectorTest, DontCheckSameNonProfileableAppAgain) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(false));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 1);
  EXPECT_THAT(output.profileables.size(), 0);

  // The subsequent refreshes shouldn't call the Check() function.
  ostringstream oss;
  detector_.Refresh(oss);
  detector_.Refresh(oss);
  detector_.Refresh(oss);
}

TEST_F(DetectorTest, CheckSameProcessIfNameChanges) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  int32_t checked_pid;
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                            Return(false)));

  ostringstream oss;
  detector_.Refresh(oss);
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.app1"));

  // Update the cmdline file to mimic the app changes its name
  AddCmdlineFile(123, "com.new.name");

  detector_.Refresh(oss);
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.new.name"));
}

TEST_F(DetectorTest, UpdateLogForNewProfileableApp) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillOnce(Return(false))  // first check returns non-profileable
      .WillOnce(Return(true));  // second check returns profileable

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.app_count, 1);
  EXPECT_THAT(output.profileables.size(), 0);

  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  auto output_2 = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output_2.app_count, 2);
  EXPECT_THAT(output_2.profileables.size(), 1);
  const auto& found_2 = output_2.profileables.find(456);
  EXPECT_THAT(found_2->second.package_name, StrEq("com.app2"));
}

TEST_F(DetectorTest, UpdateLogIfProfileableAppRestartsWithSamePid) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(Return(true));

  auto output = ParseLog(RefreshAndRetrieveLog());

  ASSERT_THAT(output.profileables.size(), 1);
  const auto& found = output.profileables.find(123);
  EXPECT_THAT(found->second.start_time, 2345);

  // Update the stat file with new start_time to mimic the app has restarted.
  AddStatFile(123, "com.app1", kZygotePid, 8888);

  auto output_2 = ParseLog(RefreshAndRetrieveLog());

  ASSERT_THAT(output_2.profileables.size(), 1);
  const auto& found_2 = output_2.profileables.find(123);
  EXPECT_THAT(found_2->second.start_time, 8888);
}

TEST_F(DetectorTest, EmptyProfileableLogSectionForNewNonProfileableApp) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillOnce(Return(true))    // first check returns profileable
      .WillOnce(Return(false));  // second check returns non-profileable

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.app_count, 1);
  ASSERT_THAT(output.profileables.size(), 1);
  const auto& found = output.profileables.find(123);
  EXPECT_THAT(found->second.package_name, StrEq("com.app1"));

  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  const string& log = RefreshAndRetrieveLog();
  // The profileable section of the log should be empty.
  EXPECT_THAT(log, Not(HasSubstr("profileable processes")));
  auto output_2 = ParseLog(log);
  EXPECT_THAT(output_2.app_count, 2);
}

TEST_F(DetectorTest, EmptyProfileableLogSectionForNewNonAppProcess) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(true));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 3);
  EXPECT_THAT(output.app_count, 1);
  ASSERT_THAT(output.profileables.size(), 1);
  const auto& found = output.profileables.find(123);
  EXPECT_THAT(found->second.package_name, StrEq("com.app1"));

  AddProcessFiles(456, "NotAnApp", 11111 /* not zygote */, 6789);

  const string& log = RefreshAndRetrieveLog();
  // The profileable section of the log should be empty.
  EXPECT_THAT(log, Not(HasSubstr("profileable processes")));
  auto output_2 = ParseLog(log);
  EXPECT_THAT(output_2.process_count, 4);
  EXPECT_THAT(output_2.app_count, 1);
}

TEST_F(DetectorTest, ProfileableAppDies) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(Return(true));

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 4);
  EXPECT_THAT(output.app_count, 2);
  EXPECT_THAT(output.profileables.size(), 2);

  // Kill the first prifileable app
  detector_.file_system()->DeleteDir("/proc/123");
  auto output_2 = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output_2.process_count, 3);
  EXPECT_THAT(output_2.app_count, 1);
  EXPECT_THAT(output_2.profileables.size(), 1);

  // Kill the second prifileable app
  detector_.file_system()->DeleteDir("/proc/456");
  const string& log = RefreshAndRetrieveLog();
  EXPECT_THAT(log, StartsWith("0 profileable processes\n"));
}

TEST_F(DetectorTest, NonProfileableAppDies) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(false))  // the first app is non-profileable
      .WillOnce(Return(true));  // the second app is profileable

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 4);
  EXPECT_THAT(output.app_count, 2);
  EXPECT_THAT(output.profileables.size(), 1);

  // Kill the non-prifileable app
  detector_.file_system()->DeleteDir("/proc/123");
  const string& log = RefreshAndRetrieveLog();
  // The profileable section of the log should be empty.
  EXPECT_THAT(log, Not(HasSubstr("profileable processes")));
  auto output_2 = ParseLog(log);
  EXPECT_THAT(output_2.process_count, 3);
  EXPECT_THAT(output_2.app_count, 1);

  // Kill the prifileable app
  detector_.file_system()->DeleteDir("/proc/456");
  const string& log_2 = RefreshAndRetrieveLog();
  EXPECT_THAT(log_2, StartsWith("0 profileable processes\n"));
}

TEST_F(DetectorTest, NonAppProcessDies) {
  AddProcessFiles(123, "NotAnApp", 11111 /* not zygote */, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true));  // the only app is profileable

  auto output = ParseLog(RefreshAndRetrieveLog());

  EXPECT_THAT(output.process_count, 4);
  EXPECT_THAT(output.app_count, 1);
  EXPECT_THAT(output.profileables.size(), 1);

  // Kill the non-app process
  detector_.file_system()->DeleteDir("/proc/123");
  const string& log_2 = RefreshAndRetrieveLog();
  // The profileable section of the log should be empty.
  EXPECT_THAT(log_2, Not(HasSubstr("profileable processes")));
  auto output_2 = ParseLog(log_2);
  EXPECT_THAT(output_2.process_count, 3);
  EXPECT_THAT(output_2.app_count, 1);

  // Kill the prifileable app
  detector_.file_system()->DeleteDir("/proc/456");
  const string& log_3 = RefreshAndRetrieveLog();
  EXPECT_THAT(log_3, StartsWith("0 profileable processes\n"));
}

}  // namespace ddmlib
