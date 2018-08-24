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
#include <unistd.h>
#include <thread>

#include "message_pipe_wrapper.h"

namespace deploy {

class MessagePipeWrapperTest : public ::testing::Test {
 public:
  void SetUp() override { signal(SIGPIPE, SIG_IGN); }
};

TEST_F(MessagePipeWrapperTest, HandleSmallMessage) {
  int pipe_fds[2];
  EXPECT_EQ(0, pipe(pipe_fds));

  MessagePipeWrapper read(pipe_fds[0]);
  MessagePipeWrapper write(pipe_fds[1]);

  std::string message(1 << 8, '\xFF');
  std::string received;
  EXPECT_TRUE(write.Write(message));
  EXPECT_TRUE(read.Read(&received));
  EXPECT_EQ(received, message);
}

TEST_F(MessagePipeWrapperTest, HandleLargeMessage) {
  int pipe_fds[2];
  EXPECT_EQ(0, pipe(pipe_fds));

  MessagePipeWrapper read(pipe_fds[0]);
  MessagePipeWrapper write(pipe_fds[1]);

  std::string message(1 << 24, '\xFF');
  std::string received;

  // Spawn another thread because the message is too big for the kernel to write
  // all at once; if there is not a simultaneous reader, the write will block.
  std::thread write_thread([&]() { EXPECT_TRUE(write.Write(message)); });
  EXPECT_TRUE(read.Read(&received));

  // Intentionally not using EXPECT_EQ, since on a failure EXPECT_EQ prints the
  // "expected" value to the screen, which in this case is roughly 17MB of data.
  EXPECT_TRUE(received == message);
  write_thread.join();
}

TEST_F(MessagePipeWrapperTest, HandleManyMessages) {
  int pipe_fds[2];
  EXPECT_EQ(0, pipe(pipe_fds));

  MessagePipeWrapper read(pipe_fds[0]);
  MessagePipeWrapper write(pipe_fds[1]);

  int lengths[10] = {35, 23, 199, 3, 1000, 482, 1, 399, 0, 18};
  for (int i = 0; i < 10; ++i) {
    EXPECT_TRUE(write.Write(std::string(lengths[i], '\xFF')));
  }

  for (int i = 0; i < 10; ++i) {
    std::string received;
    EXPECT_TRUE(read.Read(&received));
    EXPECT_EQ(received, std::string(lengths[i], '\xFF'));
  }
}

TEST_F(MessagePipeWrapperTest, TestPoll) {
  int pipe_fds[2];

  EXPECT_EQ(0, pipe(pipe_fds));
  MessagePipeWrapper read_1(pipe_fds[0]);
  MessagePipeWrapper write_1(pipe_fds[1]);

  EXPECT_EQ(0, pipe(pipe_fds));
  MessagePipeWrapper read_2(pipe_fds[0]);
  MessagePipeWrapper write_2(pipe_fds[1]);

  write_1.Write("\xEE");

  auto ready = MessagePipeWrapper::Poll({&read_1, &read_2}, 1000);
  EXPECT_EQ(ready.size(), 1);
  EXPECT_EQ(ready[0], 0);  // First pipe is ready.

  write_2.Write("\xFF");

  ready = MessagePipeWrapper::Poll({&read_1, &read_2}, 1000);
  EXPECT_EQ(ready.size(), 2);
  EXPECT_EQ(ready[0], 0);  // First pipe is ready.
  EXPECT_EQ(ready[1], 1);  // Second pipe is ready.

  std::string received;
  EXPECT_TRUE(read_1.Read(&received));
  EXPECT_TRUE(read_2.Read(&received));
  EXPECT_EQ(received, "\xEE\xFF");

  ready = MessagePipeWrapper::Poll({&read_1, &read_2}, 1000);
  EXPECT_EQ(ready.size(), 0);
}

}  // namespace deploy
