/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
#include "perfetto.h"

using std::string;

namespace profiler {

// TODO (b/126401684): Change the perfetto location when sideloading perfetto.
const char *kPerfettoExecutable = "/system/bin/perfetto";

Perfetto::Perfetto() : command_(kPerfettoExecutable) {}

void Perfetto::Run(const PerfettoArgs &run_args) {
  // Serialize the config as a binary proto.
  std::ostringstream binary_config;
  run_args.config.SerializeToOstream(&binary_config);
  // execve requires the argument array to end with a null value.
  // -c - tells perfetto to expect the config to be passed in via STDIN.
  // This is done because the app does not have write privilages to
  // /data/misc/perfetto_traces/ folder that perfetto can read from.
  const char *args[] = {kPerfettoExecutable, "-c", "-", "-o",
                        run_args.output_file_path.c_str(), NULL};
  command_.Run(args, binary_config.str());
}

}  // namespace profiler
