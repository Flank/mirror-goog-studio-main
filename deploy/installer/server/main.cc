/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/installer/server/canary.h"
#include "tools/base/deploy/installer/server/install_server.h"
#include "tools/base/deploy/installer/server/parent_monitor.h"

using namespace deploy;

// Expected Arguments
// [0] = executable_name
// [1] = package_name
int main(int argc, char* argv[]) {
  InitEventSystem();

  // Monitor parent process to stop operating when installerd dies.
  ParentMonitor::Install();

  if (argc < 2) {
    char msg[] = "Missing package name parameter. Terminating\n";
    write(STDERR_FILENO, msg, sizeof(msg));
    exit(EXIT_FAILURE);
  }

  std::string package_name = argv[1];
  Canary canary(package_name);
  canary.Init();

  close(STDERR_FILENO);
  InstallServer server(STDIN_FILENO, STDOUT_FILENO, canary);
  server.Run();

  return EXIT_SUCCESS;
}