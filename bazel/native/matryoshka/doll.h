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
#ifndef DOLL_H
#define DOLL_H

#include <memory>
#include <string>
#include <vector>

namespace matryoshka {

class Doll {
 public:
  const std::string name;
  const unsigned char* content;
  const unsigned int content_len;

  Doll(const char* name, unsigned char* content, unsigned int content_len)
      : name(name), content(content), content_len(content_len) {}

  ~Doll() { delete content; }
};

bool Open(std::vector<std::unique_ptr<Doll> >& dolls);

Doll* FindByName(std::vector<std::unique_ptr<Doll> >& dolls, std::string name);

}  // namespace matryoshka
#endif
