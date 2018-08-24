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

#include "socket.h"
#include <poll.h>
#include <iostream>

namespace deploy {

bool Socket::Open() {
  fd_ = socket(AF_UNIX, SOCK_STREAM, 0);
  return fd_ != -1;
}

bool Socket::BindAndListen(const std::string& socket_name) {
  if (fd_ == -1) {
    return false;
  }

  struct sockaddr_un addr = {0};
  addr.sun_family = AF_UNIX;

  // Abstract sockets start with a null terminator.
  addr.sun_path[0] = '\0';

  strncpy(addr.sun_path + 1, socket_name.c_str(), sizeof(addr.sun_path) - 2);
  if (bind(fd_, (const struct sockaddr*)&addr, sizeof(addr)) == -1) {
    return false;
  }

  // If we have more than 127 pending connections, we have bigger issues.
  if (listen(fd_, 128) == -1) {
    return false;
  }

  return true;
}

bool Socket::Accept(Socket* socket, int timeout_ms) {
  if (fd_ == -1) {
    return false;
  }

  // If the other socket has already been opened, don't modify it.
  if (socket->fd_ != -1) {
    return false;
  }

  pollfd pfd = {fd_, POLLIN, 0};
  if (poll(&pfd, 1, timeout_ms) != 1) {
    return false;
  }

  socket->fd_ = accept(fd_, NULL, NULL);
  return socket->fd_ != -1;
}

bool Socket::Connect(const std::string& socket_name, int timeout_ms) {
  if (fd_ == -1) {
    return false;
  }

  struct sockaddr_un addr = {0};
  addr.sun_family = AF_UNIX;
  addr.sun_path[0] = '\0';

  strncpy(addr.sun_path + 1, socket_name.c_str(), sizeof(addr.sun_path) - 2);

  pollfd pfd = {fd_, POLLIN, 0};
  if (poll(&pfd, 1, timeout_ms) != 1) {
    return false;
  }

  if (connect(fd_, (const struct sockaddr*)&addr, sizeof(addr)) != 0) {
    close(fd_);
    return false;
  }

  return true;
}

}  // namespace deploy
