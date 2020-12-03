/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <string>

namespace deploy {
namespace Sites {

std::string AppData(const std::string pkg) { return "/data/data/" + pkg + "/"; }

std::string AppCodeCache(const std::string pkg) {
  return AppData(pkg) + "code_cache/";
}

std::string AppStudio(const std::string pkg) {
  return AppCodeCache(pkg) + ".studio/";
}

std::string AppLog(const std::string pkg) {
  return AppData(pkg) + ".agent-logs/";
}

std::string AppStartupAgent(const std::string pkg) {
  return AppCodeCache(pkg) + "startup_agents/";
}

std::string AppOverlays(const std::string pkg) {
  return AppCodeCache(pkg) + ".overlay/";
}

}  // namespace Sites
}  // namespace deploy