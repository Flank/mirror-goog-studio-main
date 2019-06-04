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

#include "tools/base/debug/agent/native/async-stack/init.h"

#include <memory>

#include "tools/base/debug/agent/native/agent.h"
#include "tools/base/debug/agent/native/async-stack/inject_hooks.h"
#include "tools/base/debug/agent/native/log.h"

namespace debug {

void InitAsyncStackInstrumentation() {
  // TODO: Add more built-in capture points.
  RegisterClassTransform(std::unique_ptr<InjectionPoint>(
      new InjectionPoint("Ljava/lang/Thread;", "start", kCapture,
                         std::unique_ptr<ReceiverKey>(new ReceiverKey()))));

  RegisterClassTransform(std::unique_ptr<InjectionPoint>(
      new InjectionPoint("Ljava/lang/Thread;", "run", kInsert,
                         std::unique_ptr<ReceiverKey>(new ReceiverKey()))));

  Log::V("Async stacktrace instrumentation initialized");
}

}  // namespace debug
