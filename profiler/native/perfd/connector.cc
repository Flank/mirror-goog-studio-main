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
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/file_descriptor_utils.h"
#include "utils/socket_utils.h"

namespace {

// Try to connect to agent a few times before giving up.
// The time when the agent starts creating and listening from the socket
// |kAgentSocketName| can vary quite a bit. For example, an app can be stuck on
// waiting for debugger to attach.
// TODO: Find a fallback solution. Currently the agent blocks until it receives
// a fd from the connector. We should implement a time-out and retry protocol
// on the agent-side.
const int kRetryMaxCount = 20;
// Interval between retry to connect to agent, in microseconds.
const int kRetryIntervalUs = profiler::Clock::ms_to_us(500);

}  // namespace

namespace profiler {

void SendDaemonSocketFdToAgent(const char *agent_socket_name,
                               int daemon_socket_fd) {
  int through_fd;
  if ((through_fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
    perror("[connector] socket error");
    exit(-1);
  }

  struct sockaddr_un addr_un;
  socklen_t addr_len;
  profiler::SetUnixSocketAddr(agent_socket_name, &addr_un, &addr_len);
  int result = -1, retry = 0;
  // Make a number of attempts to connect to the agent. The agent may just be
  // starting up and not ready for receiving the connection yet.
  do {
    result = connect(through_fd, (struct sockaddr *)&addr_un, addr_len);
    if (result == -1) {
      perror("[connector] connect error");
      usleep(kRetryIntervalUs);
      retry++;
    }
  } while (result == -1 && retry <= kRetryMaxCount);

  if (result != -1) {
    profiler::SendFdThroughFd(daemon_socket_fd, through_fd);
  }
}

}  // namespace profiler
