/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "tools/base/deploy/common/socket.h"

namespace deploy {

class SocketTest : public ::testing::Test {
 public:
  void SetUp() override { signal(SIGPIPE, SIG_IGN); }
};

TEST_F(SocketTest, TestBindAndConnect) {
  // Prevent conflicts if we run in parallel in multiple processes.
  std::string socket_name = "socket-" + std::to_string(getpid());

  Socket server;
  Socket write;
  Socket read;

  EXPECT_TRUE(server.Open());
  EXPECT_TRUE(server.BindAndListen(socket_name));

  EXPECT_TRUE(write.Open());
  EXPECT_TRUE(write.Connect(socket_name));

  EXPECT_TRUE(server.Accept(&read, 1000));
  EXPECT_TRUE(write.Write("\xFF"));

  std::string received;
  EXPECT_TRUE(read.Read(&received));
  EXPECT_EQ(received, "\xFF");
}

}  // namespace deploy
