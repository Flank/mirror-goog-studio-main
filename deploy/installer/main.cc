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

#include <unistd.h>

#include <stdlib.h>
#include <iostream>

#include "apk_toolkit.h"
#include "trace.h"

using namespace deployer;

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cout << "Usage:" << argv[0] << " [packageName]" << std::endl;
    return EXIT_FAILURE;
  }

  Trace::Init();

  // TODO Add a -v "version" flag which can be used by the Deployer to check the
  // installer is up to date.

  char* packageName = argv[1];
  ApkToolkit toolkit(packageName);
  return !toolkit.extractCDsandSignatures();
}
