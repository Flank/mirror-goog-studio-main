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
#ifndef TRANSPORT_CONNECTOR_H_
#define TRANSPORT_CONNECTOR_H_

#include <string>

namespace profiler {

// Connects to an app's Agent through a unique socket address and sends control
// messages + data to it through the conneciton.
//
// |connect_arg|: This should be formatted as
// "{APP_PID}:{CONTROL_MESSAGE}[:{DATA}]".
//
// The {APP_PID} data is used for connecting to an unique unix socket server the
// Agent in each application creates.
//
// {CONTROL_MESSAGE}. Currently, perfd sends to perfa two types of control
// messages:
// |kHeartBeatRequest| - this is a simple ping ('H') to check whether
// the agent is alive. If so, the send would simply return success. No ":{DATA}"
// in this case.
// |kDaemonConnectRequest| - A message formatted as "C:%d", where 'C' signifies
// that this is a connect request, and the integer is the {DATA} representing
// the file descriptor of the client socket which the agent can use to
// communicate with the perfd grpc server.
bool ConnectAndSendDataToPerfa(const std::string& connect_arg);

}  // namespace profiler

#endif  // TRANSPORT_CONNECTOR_H_