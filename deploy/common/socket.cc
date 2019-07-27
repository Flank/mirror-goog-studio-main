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

#include "tools/base/deploy/common/socket.h"

#include <poll.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

namespace {

int InitAddr(const std::string& socket_name, struct sockaddr_un* addr) {
  addr->sun_family = AF_UNIX;
#ifdef __APPLE__
  // Mac does not support abstract sockets, use a named one for testing
  std::string name = Env::root() + "/.abstract_" + socket_name;
  strncpy(addr->sun_path, name.c_str(), sizeof(addr->sun_path) - 1);
  return SUN_LEN(addr);
#else
  // Abstract sockets start with a null terminator.
  addr->sun_path[0] = '\0';
  strncpy(addr->sun_path + 1, socket_name.c_str(), sizeof(addr->sun_path) - 2);
  return sizeof(*addr);
#endif  // __APPLE__
}

}  // namespace

bool Socket::Open() {
  fd_ = socket(AF_UNIX, SOCK_STREAM, 0);
  return fd_ != -1;
}

bool Socket::BindAndListen(const std::string& socket_name) {
  if (fd_ == -1) {
    return false;
  }

  struct sockaddr_un addr = {0};
  socklen_t len = InitAddr(socket_name, &addr);
  if (bind(fd_, (const struct sockaddr*)&addr, len) == -1) {
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

bool Socket::Connect(const std::string& socket_name) {
  if (fd_ == -1) {
    return false;
  }

  struct sockaddr_un addr = {0};
  socklen_t len = InitAddr(socket_name, &addr);
  size_t retries = 0;
  while (connect(fd_, (const struct sockaddr*)&addr, len) != 0) {
    // Connection refusal means the server might have been slow to start, so
    // allow for retries.
    if (errno != ECONNREFUSED) {
      std::string error = "Error connecting to server: ";
      error.append(strerror(errno));
      ErrEvent(error);
      return false;
    }

    if (retries >= kConnectRetries) {
      ErrEvent("Error connectiong to server: timed out waiting for connection");
      return false;
    }

    // A failed connect() leaves the socket in an invalid state, so we need to
    // close and reopen the socket before retrying.
    Close();
    if (!Open()) {
      ErrEvent("Error connecting to server: could not open socket");
      return false;
    }

    usleep(kConnectRetryMs * 1000);
    retries++;
  }

  return true;
}

}  // namespace deploy
