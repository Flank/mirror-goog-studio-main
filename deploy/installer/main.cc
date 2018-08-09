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

#include <getopt.h>
#include <stdlib.h>
#include <unistd.h>
#include <string>

#include <algorithm>
#include <iostream>
#include <map>

#include "dump.h"
#include "trace.h"
#include "workspace.h"

using namespace deployer;

void PrintUsage(char* invoked_path) {
  std::string binary_name = basename(invoked_path);
  std::cerr
      << "Usages:" << std::endl
      << binary_name << " command [command_parameters]" << std::endl
      << std::endl
      << "Commands available:" << std::endl
      << "   dump   : Extract CDs and Signatures for a given applicationID."
      << std::endl
      << "   swap   : Perform a hot-swap via JVMTI." << std::endl
      << std::endl;
}

std::string GetInstallerPath() {
  char dest[1024];
  std::fill(dest, dest + 1024, '\0');
  readlink("/proc/self/exe", dest, 1024);
  return std::string(dest);
}

int main(int argc, char** argv) {
  Trace::Init();
  Trace mainTrace("ir2_installer");
  if (argc < 2) {
    PrintUsage(argv[0]);
    return EXIT_FAILURE;
  }

  auto binary_name = argv[0];
  auto command_name = argv[1];

  // Create a workspace for filesystem operations.
  Workspace workspace(GetInstallerPath());

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
  if (!task->Run(workspace)) {
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
