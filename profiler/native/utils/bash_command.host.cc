/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "bash_command.h"

#include "sys/wait.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/trace.h"

using std::string;

namespace profiler {

bool BashCommandRunner::RunAs(const string &parameters,
                              const string &package_name,
                              string *output) const {
  // This version of the file only runs on host, for our host we don't need
  // to use runas to copy or execute files, as such we simply forward this
  // command onto the run function.
  return Run(parameters, output);
}

bool BashCommandRunner::IsRunAsCapable() {
  return true;
}

}  // namespace profiler
