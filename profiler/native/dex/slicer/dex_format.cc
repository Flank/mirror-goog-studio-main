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
#include "common.h"

#include <zlib.h>
#include <sstream>

namespace dex {

// Compute the DEX file checksum for a memory-mapped DEX file
u4 ComputeChecksum(const Header* header) {
  const u1* start = reinterpret_cast<const u1*>(header);

  uLong adler = adler32(0L, Z_NULL, 0);
  const int nonSum = sizeof(header->magic) + sizeof(header->checksum);

  return static_cast<u4>(
      adler32(adler, start + nonSum, header->file_size - nonSum));
}

// Returns the human-readable name for a primitive type
static const char* PrimitiveTypeName(char typeChar) {
  switch (typeChar) {
    case 'B': return "byte";
    case 'C': return "char";
    case 'D': return "double";
    case 'F': return "float";
    case 'I': return "int";
    case 'J': return "long";
    case 'S': return "short";
    case 'V': return "void";
    case 'Z': return "boolean";
  }
  CHECK(!"unexpected type");
  return nullptr;
}

// Converts a type descriptor to human-readable "dotted" form.  For
// example, "Ljava/lang/String;" becomes "java.lang.String", and
// "[I" becomes "int[]".
std::string DescriptorToDecl(const char* desc) {
  std::stringstream ss;

  int array_dimensions = 0;
  while (*desc == '[') {
    ++array_dimensions;
    ++desc;
  }

  if (*desc == 'L') {
    for (++desc; *desc != ';'; ++desc) {
      CHECK(*desc != '\0');
      ss << (*desc == '/' ? '.' : *desc);
    }
  } else {
    ss << PrimitiveTypeName(*desc);
  }

  CHECK(desc[1] == '\0');

  // add the array brackets
  for (int i = 0; i < array_dimensions; ++i) {
    ss << "[]";
  }

  return ss.str();
}

}  // namespace dex
