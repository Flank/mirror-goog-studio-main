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

#ifndef DEPLOY_CANARY_H
#define DEPLOY_CANARY_H

#include <string>

namespace deploy {

// Canary will sing as long as its canary file is reachable on storage system.
// It is a convenient way to find out if framework uninstalled an app under
// our feet to have a Canary try to Tweet.
class Canary {
 public:
  explicit Canary(const std::string& appId) : package_name_(appId) {}
  void Init();
  virtual bool Tweet() const;

 private:
  const std::string package_name_;
  std::string bird_path_;
};

}  // namespace deploy

#endif