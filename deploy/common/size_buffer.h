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

#ifndef DEPLOY_SIZE_BUFFER_H
#define DEPLOY_SIZE_BUFFER_H

#include <array>
#include <iostream>

namespace deploy {

// Defines the buffer class used to prefix messages.
using SizeBuffer = std::array<unsigned char, sizeof(uint32_t)>;

inline SizeBuffer SizeToBuffer(uint32_t size) {
  SizeBuffer buffer;
  for (size_t i = 0; i < buffer.size(); ++i) {
    buffer[i] = size & 0xFF;
    size = size >> 8;
  }
  return buffer;
}

inline uint32_t BufferToSize(const SizeBuffer& buffer) {
  uint32_t size = 0;
  for (size_t i = 0; i < buffer.size(); ++i) {
    uint32_t shift = 8 * i;
    size += buffer[i] << shift;
  }
  return size;
}

}  // namespace deploy

#endif
