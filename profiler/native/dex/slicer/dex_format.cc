/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "dex_format.h"

#include <zlib.h>

namespace dex {

// Compute the DEX file checksum for a memory-mapped DEX file
u4 ComputeChecksum(const Header* header) {
  const u1* start = reinterpret_cast<const u1*>(header);

  uLong adler = adler32(0L, Z_NULL, 0);
  const int nonSum = sizeof(header->magic) + sizeof(header->checksum);

  return static_cast<u4>(
      adler32(adler, start + nonSum, header->fileSize - nonSum));
}

}  // namespace dex
