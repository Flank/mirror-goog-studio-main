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
  string cmd;
  cmd.append(kRunAsExecutable);
  cmd.append(" ");
  cmd.append(package_name);
  // TODO: The single quote can interfer with parameters. Disregarding
  // this potential issue for now.
  cmd.append(" sh -c '");
  cmd.append(executable_path_);
  cmd.append(" ");
  cmd.append(parameters);
  cmd.append("'");
  return RunAndReadOutput(cmd, output);
}

bool BashCommandRunner::IsRunAsCapable() {
  DiskFileSystem fs;
  auto run_as = fs.GetFile(kRunAsExecutable);
  // Checking for run-as existance is not enough: We also need to
  // check capabilities.
  // TODO: Use listxattr (as in
  // https://groups.google.com/forum/#!topic/android-kernel/iYakEvY24n4)
  // to makes sure run-as has CAP_SETUID and CAP_SETGID capability.
  // See bug report: https://code.google.com/p/android/issues/detail?id=187955
  return run_as->Exists();
}
}  // namespace profiler
