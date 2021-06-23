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
#include "perfd/profileable/profileable_detector.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

using profiler::FileSystem;
using profiler::MemoryFileSystem;
using profiler::proto::Event;
using profiler::proto::Process;
using std::string;
using std::unique_ptr;
using testing::_;
using testing::IsEmpty;
using testing::IsFalse;
using testing::IsTrue;
using testing::Return;
using testing::SaveArg;
using testing::StrEq;

namespace profiler {

namespace {

class MockProfileableChecker final : public profiler::ProfileableChecker {
 public:
  MOCK_CONST_METHOD2(Check, bool(int32_t pid, const std::string& package_name));
};

// Helper class to handle event streaming from the EventBuffer.
class TestEventWriter final : public EventWriter {
 public:
  TestEventWriter(std::vector<proto::Event>* events,
                  std::condition_variable* cv)
      : events_(events), cv_(cv) {}

  bool Write(const proto::Event& event) override {
    events_->push_back(event);
    cv_->notify_one();
    return true;
  }

 private:
  std::vector<proto::Event>* events_;
  std::condition_variable* cv_;
};

}  // namespace

class ProfileableDetectorTest : public ::testing::Test {
 public:
  int32_t kZygote64Pid = 11;
  int32_t kZygotePid = 12;

  ProfileableDetectorTest()
      : event_buffer_(&clock_),
        detector_(
            &clock_, &event_buffer_,
            unique_ptr<FileSystem>(new MemoryFileSystem()),
            unique_ptr<ProfileableChecker>(new MockProfileableChecker())) {
    SetupZygoteFiles();
  }

  void SetUp() override {
    // Start the event writer to listen for incoming events on a separate
    // thread.
    events_.clear();
    writer_ =
        std::unique_ptr<TestEventWriter>(new TestEventWriter(&events_, &cv_));
    read_thread_ = std::unique_ptr<std::thread>(new std::thread(
        [this] { event_buffer_.WriteEventsTo(writer_.get()); }));
  }

  void TearDown() override {
    // Kill read thread to cleanly exit test.
    event_buffer_.InterruptWriteEvents();
    if (read_thread_->joinable()) read_thread_->join();
    read_thread_ = nullptr;
  }

  void AddProcessFiles(int32_t pid, const string& name, int32_t ppid,
                       int64_t start_time) {
    AddCmdlineFile(pid, name);
    AddStatFile(pid, name, ppid, start_time);
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

  // Waits on |cv_| if |event_.size()| is not equal to |expected_event_count|;
  // doesn't wait if they are equal. When |check_expectation| is true, reports
  // an error if they aren't equal after the wait or timeout.
  void WaitForEvents(int expected_event_count, bool check_expectation = true) {
    std::mutex mutex;
    std::unique_lock<std::mutex> lock(mutex);
    bool wait_for_result = cv_.wait_for(
        lock, std::chrono::milliseconds(100), [this, expected_event_count] {
          return events_.size() == expected_event_count;
        });
    if (check_expectation) {
      EXPECT_THAT(wait_for_result, IsTrue());
    }
  }

  // Variables referenced by the test below.
  FakeClock clock_;
  EventBuffer event_buffer_;
  ProfileableDetector detector_;
  std::vector<proto::Event> events_;
  std::condition_variable cv_;
  std::unique_ptr<TestEventWriter> writer_;
  std::unique_ptr<std::thread> read_thread_;

 private:
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

TEST_F(ProfileableDetectorTest, Find32bitProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  clock_.SetCurrentTime(5678);

  int32_t checked_pid{0};
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                      Return(true)));
  EXPECT_THAT(events_, IsEmpty());

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.app1"));

  EXPECT_THAT(events_[0].pid(), 123);
  EXPECT_THAT(events_[0].group_id(), 123);
  EXPECT_THAT(events_[0].kind(), Event::PROCESS);
  EXPECT_THAT(events_[0].is_ended(), IsFalse());
  const auto& data = events_[0].process().process_started().process();
  EXPECT_THAT(data.name(), StrEq("com.app1"));
  EXPECT_THAT(data.pid(), 123);
  EXPECT_THAT(data.state(), proto::Process::ALIVE);
  EXPECT_THAT(data.start_timestamp_ns(), 5678);
  EXPECT_THAT(data.exposure_level(), Process::PROFILEABLE);
}

// The same as Find32bitProfileable but the app process is 64 bit.
TEST_F(ProfileableDetectorTest, Find64bitProfileable) {
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);
  clock_.SetCurrentTime(6789);

  int32_t checked_pid{0};
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                      Return(true)));
  EXPECT_THAT(events_, IsEmpty());

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(checked_pid, 456);
  EXPECT_THAT(checked_name, StrEq("com.app2"));

  EXPECT_THAT(events_[0].pid(), 456);
  EXPECT_THAT(events_[0].group_id(), 456);
  EXPECT_THAT(events_[0].kind(), Event::PROCESS);
  EXPECT_THAT(events_[0].is_ended(), IsFalse());
  const auto& data = events_[0].process().process_started().process();
  EXPECT_THAT(data.name(), StrEq("com.app2"));
  EXPECT_THAT(data.pid(), 456);
  EXPECT_THAT(data.state(), proto::Process::ALIVE);
  EXPECT_THAT(data.start_timestamp_ns(), 6789);
  EXPECT_THAT(data.exposure_level(), Process::PROFILEABLE);
}

TEST_F(ProfileableDetectorTest, FindTwoProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true))
      .WillOnce(Return(true));
  EXPECT_THAT(events_, IsEmpty());

  detector_.Refresh();
  WaitForEvents(2);

  std::sort(events_.begin(), events_.end(),
            [](const Event& a, const Event& b) { return a.pid() < b.pid(); });

  EXPECT_THAT(events_[0].pid(), 123);
  EXPECT_THAT(events_[0].process().process_started().process().name(),
              StrEq("com.app1"));
  EXPECT_THAT(events_[1].pid(), 456);
  EXPECT_THAT(events_[1].process().process_started().process().name(),
              StrEq("com.app2"));
}

TEST_F(ProfileableDetectorTest, FindOneProfileableOneNonProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 4321);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true))
      .WillOnce(Return(false));

  detector_.Refresh();
  WaitForEvents(1);
}

TEST_F(ProfileableDetectorTest, DontCheckNonAppProcess) {
  AddProcessFiles(123, "NotAnApp", 11111 /* not zygote */, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(0);  // Shouldn't be called
  detector_.Refresh();
}

TEST_F(ProfileableDetectorTest, DontCheckSameProfileableAppAgain) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(true));

  detector_.Refresh();
  WaitForEvents(1);

  // The subsequent refreshes shouldn't call the Check() function.
  detector_.Refresh();
  detector_.Refresh();
  detector_.Refresh();
}

TEST_F(ProfileableDetectorTest, DontCheckSameNonProfileableAppAgain) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(false));

  detector_.Refresh();

  // The subsequent refreshes shouldn't call the Check() function.
  detector_.Refresh();
  detector_.Refresh();
  detector_.Refresh();
}

TEST_F(ProfileableDetectorTest, CheckSameProcessIfNameChanges) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  int32_t checked_pid;
  string checked_name;
  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(DoAll(SaveArg<0>(&checked_pid), SaveArg<1>(&checked_name),
                            Return(false)));

  detector_.Refresh();
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.app1"));

  // Update the cmdline file to mimic the app changes its name
  AddCmdlineFile(123, "com.new.name");

  detector_.Refresh();
  EXPECT_THAT(checked_pid, 123);
  EXPECT_THAT(checked_name, StrEq("com.new.name"));
}

TEST_F(ProfileableDetectorTest, NonProfileableAndThenNewProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillOnce(Return(false))  // first check returns non-profileable
      .WillOnce(Return(true));  // second check returns profileable

  detector_.Refresh();
  WaitForEvents(1, false);  // No event expected. Expect the wait to timeout.
  EXPECT_THAT(events_.size(), 0);

  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(events_[0].pid(), 456);
  EXPECT_THAT(events_[0].process().process_started().process().name(),
              StrEq("com.app2"));
}

TEST_F(ProfileableDetectorTest, AProfileableAndThenNewNonProfileable) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillOnce(Return(true))    // first check returns profileable
      .WillOnce(Return(false));  // second check returns non-profileable

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(events_[0].pid(), 123);
  EXPECT_THAT(events_[0].process().process_started().process().name(),
              StrEq("com.app1"));

  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  detector_.Refresh();
  WaitForEvents(2, false);  // No new events expected. Expect a timeout.
  EXPECT_THAT(events_.size(), 1);
}

TEST_F(ProfileableDetectorTest, EmptyProfileableLogSectionForNewNonAppProcess) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(1)
      .WillOnce(Return(true));

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(events_[0].pid(), 123);
  EXPECT_THAT(events_[0].process().process_started().process().name(),
              StrEq("com.app1"));

  AddProcessFiles(456, "NotAnApp", 11111 /* not zygote */, 6789);

  detector_.Refresh();
  WaitForEvents(2, false);  // No new events expected. Expect a timeout.
  EXPECT_THAT(events_.size(), 1);
}

TEST_F(ProfileableDetectorTest, ProfileableAppDies) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillRepeatedly(Return(true));

  detector_.Refresh();
  WaitForEvents(2);
  EXPECT_THAT(events_[0].is_ended(), IsFalse());
  EXPECT_THAT(events_[1].is_ended(), IsFalse());

  // Kill the first prifileable app
  detector_.file_system()->DeleteDir("/proc/123");

  detector_.Refresh();
  WaitForEvents(3);
  EXPECT_THAT(events_[2].pid(), 123);
  EXPECT_THAT(events_[2].is_ended(), IsTrue());

  // Kill the second prifileable app
  detector_.file_system()->DeleteDir("/proc/456");

  detector_.Refresh();
  WaitForEvents(4);
  EXPECT_THAT(events_[3].pid(), 456);
  EXPECT_THAT(events_[3].is_ended(), IsTrue());
}

TEST_F(ProfileableDetectorTest, NonProfileableAppDies) {
  AddProcessFiles(123, "com.app1", kZygotePid, 2345);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .Times(2)
      .WillOnce(Return(true))    // first check returns profileable
      .WillOnce(Return(false));  // second check returns non-profileable

  detector_.Refresh();
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);
  detector_.Refresh();

  WaitForEvents(1);
  EXPECT_THAT(events_[0].pid(), 123);
  EXPECT_THAT(events_[0].is_ended(), IsFalse());

  // Kill the non-prifileable app
  detector_.file_system()->DeleteDir("/proc/456");

  detector_.Refresh();
  WaitForEvents(2, false);  // No new events expected. Expect a timeout.
  EXPECT_THAT(events_.size(), 1);

  // Kill the prifileable app
  detector_.file_system()->DeleteDir("/proc/123");

  detector_.Refresh();
  WaitForEvents(2);
  EXPECT_THAT(events_[1].pid(), 123);
  EXPECT_THAT(events_[1].is_ended(), IsTrue());
}

TEST_F(ProfileableDetectorTest, NonAppProcessDies) {
  AddProcessFiles(123, "NotAnApp", 11111 /* not zygote */, 2345);
  AddProcessFiles(456, "com.app2", kZygote64Pid, 6789);

  EXPECT_CALL(
      *(static_cast<MockProfileableChecker*>(detector_.profileable_checker())),
      Check(_, _))
      .WillOnce(Return(true));  // the only app is profileable

  detector_.Refresh();
  WaitForEvents(1);
  EXPECT_THAT(events_[0].pid(), 456);
  EXPECT_THAT(events_[0].is_ended(), IsFalse());

  // Kill the non-app process
  detector_.file_system()->DeleteDir("/proc/123");

  detector_.Refresh();
  WaitForEvents(2, false);  // No new events expected. Expect a timeout.
  EXPECT_THAT(events_.size(), 1);

  // Kill the prifileable app
  detector_.file_system()->DeleteDir("/proc/456");

  detector_.Refresh();
  WaitForEvents(2);
  EXPECT_THAT(events_[1].pid(), 456);
  EXPECT_THAT(events_[1].is_ended(), IsTrue());
}

}  // namespace profiler
