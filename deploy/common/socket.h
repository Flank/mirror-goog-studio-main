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

#ifndef DEPLOY_SOCKET_H
#define DEPLOY_SOCKET_H

#include <string>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "message_pipe_wrapper.h"

namespace deploy {
class Socket : public MessagePipeWrapper {
 public:
  Socket() : MessagePipeWrapper(-1) {}
  virtual ~Socket() {}

  Socket(Socket&& other) : MessagePipeWrapper(std::move(other)) {}

  Socket& operator=(Socket&& other) {
    return Socket::operator=(std::move(other));
  }

  // Creates a new UNIX stream socket and obtains its file descriptor.
  bool Open();

  // Binds the socket to the specified address and starts listening for
  // incoming connections.
  bool BindAndListen(const std::string& socket_name);

  // Accepts an incoming connection on this and assigns that connection to
  // the passed-in socket.
  bool Accept(Socket* socket, int timeout_ms);

  // Connects this socket to the socket at the specified address.
  bool Connect(const std::string& socket_name, int timeout_ms);

  // Default socket binding address.
  static constexpr auto kDefaultAddress = "irsocket";

 private:
  Socket(const Socket&) = delete;
  Socket& operator=(const Socket&) = delete;
};
}  // namespace deploy

#endif
