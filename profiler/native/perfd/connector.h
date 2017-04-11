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
#ifndef PERFD_CONNECTOR_H_
#define PERFD_CONNECTOR_H_

namespace profiler {

// The command line argument indicating that perfd is establishing communication
// channel with the agent through Unix abstract socket.
const char* const kConnectCmdLineArg = "-connect";

// Sends the file descriptor of a Daemon's client socket (|daemon_socket_fd|)
// to the agent which is listening by running a Unix socket server at
// |agent_socket_name|.
void SendDaemonSocketFdToAgent(const char* agent_socket_name,
                               int daemon_socket_fd);

}  // namespace profiler

#endif  // PERFD_CONNECTOR_H_
