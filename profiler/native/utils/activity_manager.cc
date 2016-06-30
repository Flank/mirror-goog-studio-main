/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "activity_manager.h"

#include <iostream>
#include <sstream>

#include "utils/clock.h"

using std::string;

namespace {
const char *const kAmExecutable = "/system/bin/am";
}

namespace profiler {

ActivityManager::ActivityManager() : BashCommandRunner(kAmExecutable) {}

bool ActivityManager::StartProfiling(const ProfilingMode profiling_mode,
                                     const string &app_package_name,
                                     string *trace_path,
                                     string *error_string) const {
  if (profiling_mode != SAMPLING) {
    *error_string = "Only sampling profiler is currently supported";
    return false;
  }
  *trace_path = this->GenerateTracePath(app_package_name);
  string parameters;
  parameters.append("profile start ");
  if (profiling_mode == ActivityManager::INSTRUMENTED) {
    parameters.append("--sampling 0 ");
  }
  parameters.append(app_package_name);
  parameters.append(" ");
  parameters.append(*trace_path);
  return Run(parameters, error_string);
}

bool ActivityManager::StopProfiling(const string &app_package_name,
                                    string *error_string) const {
  string parameters;
  parameters.append("profile stop ");
  parameters.append(app_package_name);
  return Run(parameters, error_string);
}

bool ActivityManager::TriggerHeapDump(int pid, const std::string &file_path,
                                      std::string *error_string) const {
  std::stringstream ss;
  ss << "dumpheap " << pid << " " << file_path;
  return Run(ss.str(), error_string);
}

std::string ActivityManager::GenerateTracePath(
    const std::string &app_package_name) const {
  // TODO: The activity manager should be a component of the daemon.
  // And it should use the daemon's steady clock.
  SteadyClock clock;
  std::stringstream path;
  path << "/data/local/tmp/";
  path << app_package_name;
  path << "-";
  path << clock.GetCurrentTime();
  path << ".art_trace";
  return path.str();
}

}  // namespace profiler
