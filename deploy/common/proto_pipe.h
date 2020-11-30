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
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/utils.h"

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
      ErrEvent("Protopipe: Unable to serialize protobuffer message");
      return false;
    }
    return pipe_.Write(bytes);
  }

  // Waits up to timeout milliseconds for a message to be available from the
  // pipe, then attempts to parse the data read into the specified proto.
  bool Read(int timeout_ms, google::protobuf::MessageLite* message) {
    std::string bytes;
    if (!pipe_.Read(timeout_ms, &bytes)) {
      ErrEvent("Protopipe: Unable to read() from pipe");
      return false;
    }

    if (!message->ParseFromString(bytes)) {
      ErrEvent("Unable to parse proto message");
      return false;
    }
    return true;
  }

  void Close() { pipe_.Close(); }

 private:
  MessagePipeWrapper pipe_;
};

// A derived class that owns the fd passed in the constructor and will therefore
// close it when it is destructed.
class OwnedProtoPipe : public ProtoPipe {
 public:
  OwnedProtoPipe(int fd) : ProtoPipe(fd) {}
  virtual ~OwnedProtoPipe() { Close(); }
};

}  // namespace deploy

#endif
