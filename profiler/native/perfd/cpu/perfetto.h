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

namespace profiler {

// Arguments for running the perfetto command.
struct PerfettoArgs {
  const perfetto::protos::TraceConfig& config;
  const std::string& abi_arch;
  const std::string& output_file_path;
};

// Wrapper around the command line perfetto interface. This class launches
// perfetto command line interface, as well as the traced (perfetto server)
// and traced_probes (perfetto probe clients) to collect data for perfetto.
// It is the running of these 3 binaries that is required to capture a
// perfetto recording.
class Perfetto {
 public:
  // This is used to determine what fails to launch when run is called.
  using LaunchStatus = int;
  static const int LAUNCH_STATUS_SUCCESS = 0;
  static const int FAILED_LAUNCH_PERFETTO = 1;
  static const int FAILED_LAUNCH_TRACED = 2;
  static const int FAILED_LAUNCH_TRACED_PROBES = 4;
  static const int FAILED_LAUNCH_TRACER = 8;
  
  explicit Perfetto();
  virtual ~Perfetto() { Shutdown(); }

  // Runs perfetto, the config is serialized and passed via stdin. This is
  // required for P due to a lack of permissions overlap between the app and
  // perfetto. The output is written to the |output_file_path| howver this has
  // to be located in the /data/misc/perfetto-traces/ directory for security
  // reasons.
  virtual Perfetto::LaunchStatus Run(const PerfettoArgs& run_args);

  // Checks to see if perfetto is running, it does this by checking if we
  // launched perfetto as well as checking to see traced, and traced_probs
  // are running.
  virtual bool IsPerfettoRunning();

  // Check to see if tracer is running. This is done by reading the value
  // of the tracing_on pipe.
  virtual bool IsTracerRunning();

  // Stops the perfetto process. Any data gathered will remain in the output
  // file path. Stop does not kill the traced and traced_probes processes
  // because they manage ftrace and do critical book keeping for multiple trace
  // sessions. To kill the traced processes call Shutdown.
  virtual void Stop();

  // Shutdown stops the perfetto process if running as well as kills the traced
  // and traced_probes processes. Shutdown gets called when perfd dies.
  virtual void Shutdown();

 private:
  std::unique_ptr<NonBlockingCommandRunner> command_;
  std::unique_ptr<NonBlockingCommandRunner> traced_;
  std::unique_ptr<NonBlockingCommandRunner> traced_probes_;

  // Returns the path to the |executable| binary with the abi_arch appended.
  std::string GetPath(const char* executable,
                      const std::string& abi_arch) const;

  // Forces tracer to be turned off, this is only used when we know the
  // pipe is open due to profilers launching of perfetto. This is needed
  // if perfetto has a bug and does not close the ftrace pipe.
  // True is returned if tracer was stopped successfully.
  virtual void ForceStopTracer();

  // Helper function to launch a process and block waiting for the
  // /proc/[pid]/cmdline to be populated with the process path.
  // If the cmdline does not match the expected process path the process is
  // killed (if running) and the returned NonBlockingCommandRunning::IsRunning
  // will return false.
  std::unique_ptr<NonBlockingCommandRunner> LaunchProcessAndBlockTillStart(
      const PerfettoArgs& run_args, const char* process_name,
      const char* const env_args[]);

  // Check the stats of tracer, while it does not match |is_tracer_running|
  // sleep then try again up until a retry limit is reached.
  void WaitForTracerStatus(bool expected_tracer_running);
};

}  // namespace profiler

#endif  // PERFD_CPU_PERFETTO_H_
