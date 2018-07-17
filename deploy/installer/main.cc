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

#include <getopt.h>
#include <libgen.h>
#include <stdlib.h>
#include <iostream>

#include <map>

#include "dump.h"
#include "trace.h"

using namespace deployer;

void PrintUsage(char *invokedPath) {
  std::string binary_name = basename(invokedPath);
  std::cerr
      << "Usages:" << std::endl
      << binary_name << " command [command_parameters]" << std::endl
      << std::endl
      << "Commands available:" << std::endl
      << "   dump   : Extract CDs and Signatures for a given applicationID."
      << std::endl
      << std::endl;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    PrintUsage(argv[0]);
    return EXIT_FAILURE;
  }

  Trace::Init();

  auto binary_name = argv[0];
  auto command_name = argv[1];

  // Retrieve Command to be invoked.
  auto task = GetCommand(command_name);
  if (task == nullptr) {
    PrintUsage(binary_name);
    return EXIT_FAILURE;
  }

  // Allow command to parse its parameters and invoke it.
  task->ParseParameters(argc - 2, argv + 2);
  if (!task->ReadyToRun()) {
    return EXIT_FAILURE;
  }
  if (!task->Run()) {
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
