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

#ifndef COMMON_ENV_H
#define COMMON_ENV_H

#include <string>

namespace deploy {

// The global environment we are running on. In production this is invalid,
// but when running tests it represents the test enviroment.
class Env {
 public:
  // Force Env to reinitalize itself based on env variables.
  static void Reset();

  // Whether there is a custom environment set
  static bool IsValid();

  // A port where to communicate with a FakeDevice grpc server
  static int port();

  // Where the root folder is located. Empty in production.
  static std::string root();

  // The file where to save logcat to.
  static std::string logcat();

  // The shell binary to use when invoking commands.
  static std::string shell();

  // The API level of the current device.
  static int api_level();

  // The build type of the current device (user, userdebug, eng)
  static std::string build_type();

  // The uid of the android system. (Not the same as the actual running uid)
  static int uid();

  // Changes the current uid of the android system. No effect in production.
  static void set_uid(int uid);
};

}  // namespace deploy

#endif  // COMMON_ENV_H
