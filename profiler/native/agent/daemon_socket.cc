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
#include "agent/daemon_socket.h"

#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sstream>
#include "utils/file_descriptor_utils.h"
#include "utils/socket_utils.h"
#include "utils/log.h"

namespace profiler {

std::string GetDaemonSocketAsGrpcTarget(const char* agent_address) {
  int agent_fd = ListenToSocket(CreateUnixSocket(agent_address));
  int connector_fd = -1;
  if ((connector_fd = accept(agent_fd, nullptr, nullptr)) == -1) {
    perror("accept error");
    exit(-1);
  }
  int daemon_fd = ReceiveFdThroughFd(connector_fd);
  Log::V("Agent receives an existing client socket.");
  std::ostringstream os;
  os << kGrpcUnixSocketAddrPrefix << "&" << daemon_fd;
  return os.str();
}

}  // namespace profiler
