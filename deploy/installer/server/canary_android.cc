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

#include "tools/base/deploy/installer/server/canary.h"

#include <unistd.h>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

void Canary::Init() {
  std::string dir = Sites::AppStudio(package_name_);
  bird_path_ = dir + ".canary";

  if (Tweet()) {
    return;
  }

  IO::mkpath(dir, S_IRWXG | S_IRWXU | S_IRWXO);
  IO::creat(bird_path_, 0);
}

bool Canary::Tweet() const { return IO::access(bird_path_, F_OK) != -1; }
}  // namespace deploy