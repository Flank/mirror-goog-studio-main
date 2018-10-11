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

#ifndef SWAP_ACTION_H_
#define SWAP_ACTION_H_

#include <memory>
#include <string>

#include <jni.h>
#include <jvmti.h>

namespace proto {
class SwapRequest;
class AgentSwapResponse;
}  // namespace proto

namespace deploy {
class Socket;

class Swapper {
 public:
  ~Swapper() = default;

  // Sets the Swapper instance's jvmti environment and socket, cleaning up any
  // previously set socket, request, or jvmti environment.
  void Initialize(jvmtiEnv* jvmti, std::unique_ptr<Socket> socket);

  // Reads a SwapRequest from the underlying socket, then performs a swap using
  // the specified JNI environment, which should be the JNI environment attached
  // to the currently executing thread.
  void StartSwap(JNIEnv* jni);

  // Returns the current swapper instance.
  static Swapper& Instance();

 private:
  jvmtiEnv* jvmti_;
  std::unique_ptr<Socket> socket_;
  std::unique_ptr<proto::SwapRequest> request_;

  static Swapper* instance_;

  Swapper();
  Swapper(const Swapper&) = delete;
  Swapper& operator=(const Swapper&) = delete;

  void SendResponse(proto::AgentSwapResponse& response);

  // Frees the memory associated with the socket and request objects, and closes
  // the socket. Destroys the JVMTI environment.
  void Reset();
};
}  // namespace deploy

#endif
