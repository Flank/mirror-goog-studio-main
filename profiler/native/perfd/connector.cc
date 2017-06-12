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

#include <stdlib.h>
#include <unistd.h>
#include <cassert>
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/socket_utils.h"

namespace {

// In the case where we are sending a connect request to agent, try to
// connect a few times before giving up. The time when the agent starts
// creating and listening from the socket |kAgentSocketName| can vary quite a
// bit. For example, an app can be stuck on waiting for debugger to attach.
const int kRetryMaxCount = 20;
// Interval between retry to connect to agent, in microseconds.
const int kTimeoutUs = profiler::Clock::ms_to_us(500);

}  // namespace

namespace profiler {

bool ConnectAndSendDataToPerfa(const std::string& connect_arg,
                               const std::string& control_arg) {
  // Parses the app's process id from |connect_arg| to construct the target
  // agent socket we want to connect to.
  int delimiter_index = connect_arg.find('=');
  assert(delimiter_index != -1);
  std::string app_socket(profiler::kAgentSocketName);
  app_socket.append(connect_arg.substr(delimiter_index + 1));

  std::string control = control_arg.substr(0, 1);
  int daemon_socket_fd = -1;
  int retry_count = 0;
  if (control.compare(profiler::kHeartBeatRequest) == 0) {
    // Send heartbeat. No additional parsing needed.
  } else if (control.compare(profiler::kPerfdConnectRequest) == 0) {
    // Connect request. Parse the file descriptor as well.
    delimiter_index = control_arg.find('=');
    assert(delimiter_index != -1);
    daemon_socket_fd = atoi(control_arg.c_str() + delimiter_index + 1);

    // Make a number of attempts to connect to the agent. The agent may just be
    // starting up and not ready for receiving the connection yet.
    retry_count = kRetryMaxCount;
  }

  int sent_count = profiler::ConnectAndSendDataToSocket(
      app_socket.c_str(), daemon_socket_fd, control.c_str(), retry_count,
      kTimeoutUs);

  // Sent |control| data is of length 1.
  return sent_count == 1;
}

}  // namespace profiler