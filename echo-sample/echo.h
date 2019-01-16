/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef ECHO_DAEMON_HANDLER_H_
#define ECHO_DAEMON_HANDLER_H_

#include <unordered_map>

#include "daemon/daemon.h"

namespace demo {

/**
 * This example class shows how to register a simple command handler using the
 * daemon. This is required for command processing in the transport pipeline.
 * The Initialize function is called from transport main.
 */
class Echo final {
 public:
  static void Initialize(profiler::Daemon* daemon);
};

}  // namespace demo

#endif  // ECHO_DAEMON_HANDLER_H_
