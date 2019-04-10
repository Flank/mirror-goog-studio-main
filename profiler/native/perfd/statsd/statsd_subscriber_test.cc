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
#include "statsd_subscriber.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "pulled_atoms/wifi_bytes_transfer.h"
#include "utils/fake_clock.h"

using ::testing::_;
using ::testing::A;
using ::testing::AtLeast;
using ::testing::NiceMock;
using ::testing::Return;

namespace profiler {

class MockNonBlockingCommandRunner : public NonBlockingCommandRunner {
 public:
  explicit MockNonBlockingCommandRunner()
      : NonBlockingCommandRunner("foo", true) {}
  MOCK_METHOD4(Run,
               bool(const char* const arguments[], const std::string& input,
                    StdoutCallback* callback, const char* const env_args[]));
  MOCK_METHOD0(Kill, void());
  MOCK_METHOD0(IsRunning, bool());
};

class StatsdSubscriberTest : public ::testing::Test {
 protected:
  StatsdSubscriberTest() {}
};

TEST_F(StatsdSubscriberTest, FindsSubscrbiedAtoms) {
  FakeClock clock;
  StatsdSubscriber::Instance().SubscribeToPulledAtom(
      std::unique_ptr<WifiBytesTransfer>(
          new WifiBytesTransfer(1, 1, &clock, nullptr)));
  auto* wifi_bytes_transfer =
      StatsdSubscriber::Instance().FindAtom<WifiBytesTransfer>(10000);
  EXPECT_NE(nullptr, wifi_bytes_transfer);
  EXPECT_EQ(1, wifi_bytes_transfer->pid());
}

TEST_F(StatsdSubscriberTest, RunsAndStopsCommand) {
  FakeClock clock;
  NiceMock<MockNonBlockingCommandRunner> mock_runner;
  StatsdSubscriber statsd(&mock_runner);
  ON_CALL(mock_runner, IsRunning).WillByDefault(Return(false));
  ON_CALL(mock_runner, Run(_, _, _, _)).WillByDefault(Return(true));
  EXPECT_CALL(mock_runner, Run(_, _, _, _)).Times(1);
  EXPECT_CALL(mock_runner, Kill).Times(AtLeast(1));
  statsd.SubscribeToPulledAtom(std::unique_ptr<WifiBytesTransfer>(
      new WifiBytesTransfer(1, 1, &clock, nullptr)));
  statsd.Run();
  statsd.Stop();
}

}  // namespace profiler
