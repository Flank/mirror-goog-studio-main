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

socklen_t InitAddr(const std::string& socket_name, struct sockaddr_un* addr) {
  addr->sun_family = AF_UNIX;
#ifdef __APPLE__
  // Mac does not support abstract sockets, use a named one for testing
  std::string name = "/tmp/.abstract_" + socket_name;
  strncpy(addr->sun_path, name.c_str(), sizeof(addr->sun_path) - 1);
  return SUN_LEN(addr);
#else
  // Abstract socket paths start with a null terminator and don't need to
  // include an ending null.
  addr->sun_path[0] = '\0';
  addr->sun_path[1] = '\0';

  // Make sure we account for the fact that strncat adds a null terminator so we
  // don't overflow the buffer. We don't count this null terminator when
  // returning the length of the address struct.
  strncat(addr->sun_path + 1, socket_name.c_str(), sizeof(addr->sun_path) - 2);
  return sizeof(sa_family_t) +
         std::min(socket_name.size() + 1, sizeof(addr->sun_path) - 1);
#endif  // __APPLE__
}

}  // namespace

const std::string Socket::kDefaultAddressPrefix = "irsocket-";

bool Socket::Open() {
  fd_ = socket(AF_UNIX, SOCK_STREAM, 0);
  return fd_ != -1;
}

Socket::~Socket() {
#ifdef __APPLE__
  if (is_socket_server_) {
    struct sockaddr_un addr;
    socklen_t address_size = sizeof(sockaddr_un);

    if (!getsockname(fd_, (struct sockaddr*)&addr, &address_size)) {
      unlink(addr.sun_path);
    }
  }
#endif
  Close();
}

bool Socket::BindAndListen(const std::string& socket_name) {
  if (fd_ == -1) {
    return false;
  }

  struct sockaddr_un addr = {0};
  socklen_t len = InitAddr(socket_name, &addr);
#ifdef __APPLE__
  // Unlink the named domain socket just in case it was not properly unlinked
  // last time.
  unlink(addr.sun_path);
#endif

  if (bind(fd_, (const struct sockaddr*)&addr, len) == -1) {
    return false;
  }

  // If we have more than 127 pending connections, we have bigger issues.
  if (listen(fd_, 128) == -1) {
    return false;
  }

#ifdef __APPLE__
  is_socket_server_ = true;
#endif
  return true;
}

std::unique_ptr<Socket> Socket::Accept(int timeout_ms) {
  if (fd_ == -1) {
    ErrEvent("Attempt to Accept() before Open()");
    return nullptr;
  }

  pollfd pfd = {fd_, POLLIN, 0};
  if (poll(&pfd, 1, timeout_ms) != 1) {
    ErrEvent("poll() before accept() timeout");
    return nullptr;
  }

  std::unique_ptr<Socket> s(new Socket());
  s->fd_ = accept(fd_, NULL, NULL);
  if (s->fd_ == -1) {
    return nullptr;
  }

  return s;
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
