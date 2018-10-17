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

#ifndef DEPLOY_MESSAGE_PIPE_WRAPPER_H
#define DEPLOY_MESSAGE_PIPE_WRAPPER_H

#include <string>
#include <vector>

namespace deploy {

// Class which wraps a file descriptor and provides methods to communicate
// between different deploy components (agent, agent server, installer, etc).
// Each message is sent with a size_t prefix indicating the number of bytes
// that should be read to receive the complete message.
class MessagePipeWrapper {
 public:
  MessagePipeWrapper(int fd) : fd_(fd) {}
  virtual ~MessagePipeWrapper() {}

  MessagePipeWrapper(MessagePipeWrapper&& other)
      : MessagePipeWrapper(std::move(other.fd_)) {
    other.fd_ = -1;
  }

  MessagePipeWrapper& operator=(MessagePipeWrapper&& other) {
    fd_ = std::move(other.fd_);
    other.fd_ = -1;
    return *this;
  }

  // Writes a message to the specified file descriptor. Blocks until the write
  // completes or an error occurs.
  bool Write(const std::string& message) const;

  // Reads a message from the specified file descriptor. Blocks until the read
  // completes or an error occurs.
  bool Read(std::string* message) const;

  // Closes the fd.
  void Close();

  // Waits for data on the specified wrappers. Returns a vector containing the
  // positions of the wrappers with data to read.
  static std::vector<size_t> Poll(
      const std::vector<MessagePipeWrapper*>& wrappers, int timeout_ms);

 protected:
  int fd_;

 private:
  MessagePipeWrapper(const MessagePipeWrapper&) = delete;
  MessagePipeWrapper& operator=(const MessagePipeWrapper&) = delete;

  template <typename T>
  bool ReadBytes(T* array, size_t size) const;

  template <typename T>
  bool WriteBytes(T* array, size_t size) const;
};

// A derived class that owns the fd passed in the constructor and will therefore
// close it when it is destructed.
class OwnedMessagePipeWrapper: public MessagePipeWrapper {
 public:
  OwnedMessagePipeWrapper(int fd) : MessagePipeWrapper(fd) {}
  virtual ~OwnedMessagePipeWrapper() {
    Close();
  }
};
}  // namespace deploy

#endif
