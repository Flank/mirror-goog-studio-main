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
#include "doll.h"

#include <algorithm>
#include <fstream>
#include <iostream>

#ifdef __APPLE__
#include <mach-o/dyld.h>
#endif

using namespace std;

namespace matryoshka {

// From the ios::cur of 'self', read back 'size' number of bytes into 'content'.
static inline void ReadBack(ifstream& self, unsigned char* content, int size) {
  self.seekg(-size, ios::cur);
  self.read((char*) content, size);
  self.seekg(-size, ios::cur);
}

namespace {
bool Open(std::vector<std::unique_ptr<Doll> >& dolls, std::string* target) {

#ifdef __APPLE__
  char path[1024];
  uint32_t psize = sizeof(path);
  if (_NSGetExecutablePath(path, &psize) != 0) {
    // Failed to get executable.
    return false;
  }
  ifstream self(path, ios::binary);
#else
  ifstream self("/proc/self/exe", ios::binary);
#endif
  self.seekg(0, ios::end);

  // Check the Magic Number
  unsigned int magic_number;
  ReadBack(self, (unsigned char*)&magic_number, 4);
  if (magic_number != 0xd1d50655) {
    return false;
  }

  // Read number of payload files.
  int size;
  ReadBack(self, (unsigned char*) &size, 4);

  for (int i = 0; i < size; i++) {
    // First read filename.
    int name_len;
    ReadBack(self, (unsigned char*)&name_len, 4);
    char* name = new char[name_len + 1];
    ReadBack(self, (unsigned char*)name, name_len);
    name[name_len] = '\0';

    int file_size;
    ReadBack(self, (unsigned char*)&file_size, 4);

    // Then read the content of the file.
    if (target && target->compare(name) != 0) {
      // Skips over the content that isn't the name of the target.
      self.seekg(-file_size, ios::cur);
    } else {
      unsigned char* file = new unsigned char[file_size];
      ReadBack(self, file, file_size);
      Doll* doll = new Doll(name, file, file_size);
      dolls.emplace_back(doll);
    }
  }
  return true;
}
} // namespace

bool Open(std::vector<std::unique_ptr<Doll> >& dolls) {
  return Open(dolls, nullptr);
}

Doll* OpenByName(std::string name) {
  std::vector<std::unique_ptr<Doll>> dolls;
  if (Open(dolls, &name) && !dolls.empty()) {
    return dolls.front().release();
  } else {
    return nullptr;
  }
}

Doll* FindByName(vector<unique_ptr<Doll> >& dolls, std::string name) {
  for (auto const& doll: dolls) {
    if (doll->name.compare(name) == 0) {
      return doll.get();
    }
  }
  return nullptr;
}
}  // namespace matryoshka
