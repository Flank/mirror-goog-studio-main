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

#include <stddef.h>
#include <string>

#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// std::literals::string_literals::operator""s is only available in c++14. Until
// we switch NDK to move up from c++11, this is our own syntax sugar via user
// defined literal.
inline const std::string operator"" _s(const char* c, std::size_t size) {
  return std::string(c, size);
}

void LogEvent(proto::Event* event, const std::string& message);
void ErrEvent(proto::Event* event, const std::string& message);
}  // namespace deployer