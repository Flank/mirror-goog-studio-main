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

using namespace std;

namespace matryoshka {

// From the ios::cur of 'self', read back 'size' number of bytes into 'content'.
static inline void ReadBack(ifstream& self, unsigned char* content, int size) {
  self.seekg(-size, ios::cur);
  self.read((char*) content, size);
  self.seekg(-size, ios::cur);
}

bool Open(std::vector<std::unique_ptr<Doll> >& dolls) {
  ifstream self("/proc/self/exe", ios::binary);
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
    unsigned char* file = new unsigned char[file_size];
    ReadBack(self, file, file_size);

    Doll* doll = new Doll(name, file, file_size);
    dolls.emplace_back(doll);
  }
  return true;
}

Doll* FindByName(vector<unique_ptr<Doll> >& dolls, std::string name) {
  for(auto const& doll: dolls) {
    if(doll->name.compare(name) == 0) {
      return doll.get();
    }
  }
  return nullptr;
}
}  // namespace matryoshka
