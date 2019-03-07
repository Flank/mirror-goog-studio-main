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
 */

#ifndef PERFD_CPU_PERFETTO_H_
#define PERFD_CPU_PERFETTO_H_

#include <set>
#include <string>
#include <thread>

#include "protos/perfetto/config/perfetto_config.grpc.pb.h"
#include "utils/clock.h"
#include "utils/nonblocking_command_runner.h"
#include "utils/fs/file_system.h"

namespace profiler {

// Arguments for running the perfetto command.
struct PerfettoArgs {
  const perfetto::protos::TraceConfig& config;
  const std::string& output_file_path;
};

class Perfetto {
 public:
  explicit Perfetto();
  virtual ~Perfetto() { Stop(); }

  // Runs perfetto, the config is serialized and passed via stdin. This is
  // required for P due to a lack of permissions overlap between the app and
  // perfetto. The output is written to the |output_file_path| howver this has
  // to be located in the /data/misc/perfetto-traces/ directory for security
  // reasons.
  virtual void Run(const PerfettoArgs& run_args);

  // Checks to see if perfetto is running.
  virtual bool IsPerfettoRunning() { return command_.IsRunning(); }

  // Stops the perfetto process. Any data gathered will remain in the output
  // file path.
  virtual void Stop() { command_.Kill(); }

 private:
  NonBlockingCommandRunner command_;
};

}  // namespace profiler

#endif  // PERFD_CPU_PERFETTO_H_
