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

#ifndef AGENT_H_
#define AGENT_H_

#include <memory>

#include "tools/base/debug/agent/native/transform.h"

namespace debug {

// Registers a transformation to be applied when its target class is loaded.
void RegisterClassTransform(std::unique_ptr<ClassTransform> transform);

}  // namespace debug

#endif  // AGENT_H_
