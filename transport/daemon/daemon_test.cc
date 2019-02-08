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
#include "daemon.h"

#include <gmock/gmock.h>
#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "commands/command.h"
#include "event_buffer.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/transport.grpc.pb.h"
#include "utils/config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

using testing::Return;

namespace profiler {

class DaemonTest : public ::testing::Test {
 public:
  DaemonTest()
      : file_cache_(std::unique_ptr<FileSystem>(new MemoryFileSystem()), "/"),
        config_(agent_config_),
        buffer_(&clock_),
        daemon_(&clock_, &config_, &file_cache_, &buffer_) {}

  FakeClock clock_;
  FileCache file_cache_;
  proto::AgentConfig agent_config_;
  Config config_;
  EventBuffer buffer_;
  Daemon daemon_;
};

class MockCommand final : public CommandT<MockCommand> {
 public:
  // Provide singleton instance so we can operate on the same mock object.
  static MockCommand *Instance(const proto::Command &command) {
    static MockCommand *instance = new MockCommand(command);
    return instance;
  }
  MOCK_METHOD1(ExecuteOn, grpc::Status(Daemon *daemon));

 private:
  explicit MockCommand(const proto::Command &command) : CommandT(command) {}
};

TEST_F(DaemonTest, RegisteredCommandIsHandled) {
  proto::Command command;
  command.set_type(proto::Command::BEGIN_SESSION);
  auto mock_command = MockCommand::Instance(command);

  // Register a handler that always returns the same mock.
  daemon_.RegisterCommandHandler(proto::Command::BEGIN_SESSION,
                                 &MockCommand::Instance);

  ON_CALL(*mock_command, ExecuteOn(&daemon_))
      .WillByDefault(Return(grpc::Status::OK));
  EXPECT_CALL(*mock_command, ExecuteOn(&daemon_)).Times(1);

  EXPECT_TRUE(daemon_.Execute(command).ok());
}

TEST_F(DaemonTest, UnregisteredCommandReturnsOk) {
  proto::Command command;
  command.set_type(proto::Command::BEGIN_SESSION);

  EXPECT_TRUE(daemon_.Execute(command).ok());
}
}  // namespace profiler
