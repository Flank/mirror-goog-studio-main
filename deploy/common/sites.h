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

#ifndef DEPLOY_SITES_H
#define DEPLOY_SITES_H

#include <string>

namespace deploy {
namespace Sites {

// Centralize path building management.
// All paths refering to a directory are / terminated.
std::string AppCodeCache(const std::string pkg);
std::string AppStudio(const std::string pkg);
std::string AppLog(const std::string pkg);
std::string AppStartupAgent(const std::string pkg);
std::string AppOverlays(const std::string pkg);
}  // namespace Sites
}  // namespace deploy
#endif