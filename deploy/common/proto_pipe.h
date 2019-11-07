/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef PROTO_PIPE_H
#define PROTO_PIPE_H

#include <google/protobuf/message_lite.h>

#include "tools/base/deploy/common/message_pipe_wrapper.h"

namespace deploy {

// A class that exposes methods to easily serialize and send proto messages, as
// well as wait for proto responses.
class ProtoPipe {
 public:
  ProtoPipe(int fd) : pipe_(fd) {}

  // Writes a serialized protobuf message to the pipe.
  bool Write(const google::protobuf::MessageLite& message) {
    std::string bytes;
    if (!message.SerializeToString(&bytes)) {
      return false;
    }
    return pipe_.Write(bytes);
  }

  // Waits up to timeout milliseconds for a message to be available from the
  // pipe, then attempts to parse the data read into the specified proto.
  bool Read(int timeout_ms, google::protobuf::MessageLite* message) {
    std::string bytes;

    // TODO: Fix this when we fix MessagePipeWrapper to not take a vector.
    auto ready = MessagePipeWrapper::Poll({&pipe_}, timeout_ms);
    if (ready.size() == 0) {
      return false;
    }

    if (!pipe_.Read(&bytes)) {
      return false;
    }

    return message->ParseFromString(bytes);
  }

  void Close() { pipe_.Close(); }

 private:
  MessagePipeWrapper pipe_;
};

}  // namespace deploy

#endif
