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

#include "tools/base/deploy/common/message_pipe_wrapper.h"

#include <poll.h>
#include <unistd.h>

#include "tools/base/deploy/common/size_buffer.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

bool MessagePipeWrapper::Write(const std::string& message) const {
  SizeBuffer size_bytes = SizeToBuffer(message.size());
  if (!WriteBytes(size_bytes.data(), size_bytes.size())) {
    return false;
  }

  if (!WriteBytes(message.c_str(), message.size())) {
    return false;
  }

  return true;
}

bool MessagePipeWrapper::Read(std::string* message) const {
  SizeBuffer size_bytes;
  size_bytes.fill(0);

  if (!ReadBytes(size_bytes.data(), size_bytes.size())) {
    return false;
  }

  size_t size = BufferToSize(size_bytes);
  std::vector<char> buffer(size);

  if (!ReadBytes(buffer.data(), size)) {
    return false;
  }

  message->append(buffer.data(), buffer.size());
  return true;
}

void MessagePipeWrapper::Close() {
  if (fd_ != -1) {
    close(fd_);
    fd_ = -1;
  }
}

std::vector<size_t> MessagePipeWrapper::Poll(
    const std::vector<MessagePipeWrapper*>& wrappers, int timeout_ms) {
  std::vector<pollfd> fds;
  for (auto& wrapper : wrappers) {
    fds.push_back({wrapper->fd_, POLLIN, 0});
  }

  int count = poll(fds.data(), fds.size(), timeout_ms);

  std::vector<size_t> ready;
  if (count <= 0) {
    return ready;
  }

  // We want to report any possible error condition on the file descriptor.
  // TODO(noahz): Make this more granular when refactoring.
  constexpr short mask = POLLIN | POLLHUP | POLLERR | POLLNVAL;
  for (size_t i = 0; i < fds.size(); ++i) {
    if (fds[i].revents & mask) {
      ready.emplace_back(i);
    }
  }

  return ready;
}

template <typename T>
bool MessagePipeWrapper::ReadBytes(T* array, size_t size) const {
  Phase p("ReadBytes: " + to_string(size));
  size_t count = 0;
  while (count < size) {
    ssize_t len = read(fd_, array + count, size - count);

    // A length of zero indicates EOF has been received; we treat this as
    // failure, since that implies we were unable to fully read <size> bytes.
    if (len <= 0) {
      return false;
    }
    count += len;
  }
  return true;
}

template <typename T>
bool MessagePipeWrapper::WriteBytes(T* array, size_t size) const {
  size_t count = 0;
  while (count < size) {
    ssize_t len = write(fd_, array + count, size - count);

    // A length of zero indicates EOF has been received; we treat this as
    // failure, since that implies we were unable to fully write <size> bytes.
    if (len <= 0) {
      return false;
    }
    count += len;
  }
  return true;
}

}  // namespace deploy
