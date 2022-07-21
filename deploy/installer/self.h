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

#ifndef INSTALLER_SELF_H_
#define INSTALLER_SELF_H_

#include <string>

namespace deploy {

// A class to monitor if the binary associated with this process is still on
// the filesystem.

class Self {
 public:
  Self();
  // Return true if what is pointed to by /proc/self/exe is gone from the
  // filesystem.
  // Note: Only implemented on Android.
  bool gone();

 private:
  std::string binary_full_path;
};
}  // namespace deploy
#endif  // INSTALLER_SELF_H_
