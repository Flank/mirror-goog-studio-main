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

#ifdef __APPLE__
#include <mach-o/dyld.h>
#endif

#include "command_cmd.h"
#include "dump.h"
#include "package_manager.h"
#include "trace.h"
#include "workspace.h"

using namespace deployer;

extern const char* kVersion_hash;

struct Parameters {
  const char* binary_name = nullptr;
  const char* command_name = nullptr;
  const char* cmd_path = nullptr;
  const char* pm_path = nullptr;
  const char* version = nullptr;
  int consumed = 0;
};

void PrintUsage(const char* invoked_path) {
  std::cerr << "Usage:" << std::endl
            << invoked_path << " [env parameters] command [command_parameters]"
            << std::endl
            << std::endl
            << "Environment parameters available:" << std::endl
            << "  -cmd=X: Define path to cmd executable (to mock android)."
            << std::endl
            << "  -pm=X : Define path to package manager executable (to mock "
               "android)."
            << std::endl
            << "  -version=X : Program will fail if version != X." << std::endl
            << "Commands available:" << std::endl
            << "   dump : Extract CDs and Signatures for a given applicationID."
            << std::endl
            << "   swap : Perform a hot-swap via JVMTI." << std::endl
            << std::endl;
}

bool ParseParameters(int argc, char** argv, Parameters* parameters) {
  parameters->binary_name = argv[0];
  parameters->consumed = 1;

  int index = 1;
  while (index < argc && argv[index][0] == '-') {
    strtok(argv[index], "=");
    if (!strncmp("-cmd", argv[index], 4)) {
      parameters->cmd_path = strtok(nullptr, "=");
    } else if (!strncmp("-pm", argv[index], 3)) {
      parameters->pm_path = strtok(nullptr, "=");
    } else if (!strncmp("-version", argv[index], 8)) {
      parameters->version = strtok(nullptr, "=");
    } else {
      std::cerr << "environment parameter unknown:" << argv[index] << std::endl;
      return false;
    }
    parameters->consumed++;
    index++;
  }

  if (index < argc) {
    parameters->command_name = argv[index];
    parameters->consumed++;
  }
  return true;
}

std::string GetInstallerPath() {
#ifdef __APPLE__
  uint32_t size = 1024;
  char dest[size];
  std::fill(dest, dest + size, '\0');
  _NSGetExecutablePath(dest, &size);
#else
  int size = 1024;
  char dest[size];
  std::fill(dest, dest + size, '\0');
  readlink("/proc/self/exe", dest, size);
#endif
  return std::string(dest);
}

int main(int argc, char** argv) {
  Trace::Init();
  Trace mainTrace("installer");
  if (argc < 2) {
    PrintUsage(argv[0]);
    return EXIT_FAILURE;
  }

  Parameters parameters;
  bool parametersParsed = ParseParameters(argc, argv, &parameters);
  if (!parametersParsed) {
    std::cerr << "Unable to parse env parameters." << std::endl;
    return EXIT_FAILURE;
  }

  if (parameters.cmd_path != nullptr) {
    CmdCommand::SetPath(parameters.cmd_path);
  }
  if (parameters.pm_path != nullptr) {
    PackageManager::SetPath(parameters.pm_path);
  }

  if (parameters.version != nullptr) {
    if (strcmp(parameters.version, kVersion_hash)) {
      // TODO: Output a Response object so version failure can be differentiated
      // from actual error.
      // For now fake a "not found" response so ddmlib client which cannot
      // retrieve status code will push a new version
      std::cerr << "/system/bin/sh: /data/local/tmp/.studio/bin/installer:"
                << std::endl;
      std::cerr << " Version mismatch, requested '" << parameters.version
                << "' but this is '" << kVersion_hash << "'" << std::endl;
      return EXIT_FAILURE;
    }
  }

  // Retrieve Command to be invoked.
  auto task = GetCommand(parameters.command_name);
  if (task == nullptr) {
    std::cerr << "Command '" << parameters.command_name << "' unknown."
              << std::endl;
    PrintUsage(parameters.binary_name);
    return EXIT_FAILURE;
  }

  // Allow command to parse its parameters and invoke it.
  task->ParseParameters(argc - parameters.consumed, argv + parameters.consumed);
  if (!task->ReadyToRun()) {
    return EXIT_FAILURE;
  }

  // Create a workspace for filesystem operations.
  Workspace workspace(GetInstallerPath());
  if (!workspace.Valid()) {
    return EXIT_FAILURE;
  }

  if (!task->Run(workspace)) {
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
