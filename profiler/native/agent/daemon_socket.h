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
#ifndef AGENT_DAEMON_SOCKET_H_
#define AGENT_DAEMON_SOCKET_H_

#include <string>

namespace profiler {

// Creates a Unix raw socket server at |agent_address|, waits for the connector
// who is expected to send the file descriptor (as an integer) of an existing
// client socket that's already connected to the daemon. Returns the received
// file descriptor in the GRPC format, e.g., "unix:&123".
std::string GetDaemonSocketAsGrpcTarget(const char* agent_address);

}  // namespace profiler

#endif  // AGENT_DAEMON_SOCKET_H_
