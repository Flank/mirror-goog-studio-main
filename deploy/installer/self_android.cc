/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "tools/base/deploy/installer/self.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/sites/sites.h"

#include <unistd.h>

namespace deploy {
Self::Self() { binary_full_path = Sites::InstallerPath(); }

bool Self::gone() {
  bool there = IO::access(binary_full_path.c_str(), F_OK) == 0;
  if (!there) {
    std::string msg = "Self-Checking '" + binary_full_path + "' NOT FOUND!";
    WarnEvent(msg.c_str());
  }
  return !there;
}

}  // namespace deploy