/*
onioj * Copyright (C) 2016 The Android Open Source Project
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

#include <fstream>
#include <iostream>

using namespace std;
using namespace matryoshka;

int main(int argc, char* argv[]) {
  vector<unique_ptr<Doll>> dolls;
  matryoshka::Open(dolls);

  if (dolls.empty()) {
    return 0;
  }
  cout << "Total of " << dolls.size() << " executables" << endl;

  cout << "Writing a.out to " << argv[1] << endl;
  ofstream outfile;
  outfile.open(argv[1], ios::out | ios::app | ios::binary);
  Doll* doll = matryoshka::FindByName(dolls, "a.out");

  outfile.write((char*) doll->content, doll->content_len);
  return 0;
}
