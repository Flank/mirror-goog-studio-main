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
#include <climits>
#include <unordered_set>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "perfd/termination_service.h"

#define READ 0
#define WRITE 1

using testing::MatchesRegex;

namespace profiler {

class TerminationServiceTest : public ::testing::Test {
 public:
  TerminationServiceTest() {}
};

TEST_F(TerminationServiceTest, TestSegfaultOutput) {
  int channel[2];
  ASSERT_NE(pipe(channel), -1);
  int child = fork();
  ASSERT_NE(child, -1);
  if (child == 0) {
    // Child process
    // Close unused read pipe.
    close(channel[READ]);
    // Redirect output to new pipe.
    dup2(channel[WRITE], STDOUT_FILENO);
    // Force and expect segfault exit.
    EXPECT_EXIT(SignalHandlerSigSegv(SIGSEGV),
                ::testing::KilledBySignal(SIGSEGV),
                "Killed by Segmentation Fault.");
  } else {
    // Close unused write channel.
    close(channel[WRITE]);
    const int bufSize = 1024;
    char buffer[bufSize];
    memset(buffer, 0, bufSize);
    read(channel[READ], &buffer, bufSize);
    // We expect the output string to start with this specific text.
    EXPECT_THAT(buffer, MatchesRegex("Perfd Segmentation Fault: [0-9]+,.*"));
    // Clean up read channel.
    close(channel[READ]);
  }
}

}  // namespace profiler