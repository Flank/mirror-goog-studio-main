/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "tools/base/deploy/installer/server/app_servers.h"

#include <unordered_map>

namespace deploy {
namespace AppServers {
namespace {
std::unordered_map<std::string, InstallClient*> clients;
}

InstallClient* Get(const std::string& package_name,
                   const std::string& tmp_folder, const std::string& version) {
  const std::string server_binary_path = tmp_folder + kInstallServer;
  if (clients.find(package_name) == clients.end()) {
    clients[package_name] =
        new InstallClient(package_name, server_binary_path, version);
  }
  return clients[package_name];
}

void Clear() {
  for (auto& pair : clients) {
    delete pair.second;
  }
}
}  // namespace AppServers
}  // namespace deploy