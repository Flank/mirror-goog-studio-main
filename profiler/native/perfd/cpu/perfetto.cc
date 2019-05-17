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
#include "utils/current_process.h"

#include <fcntl.h>  // library for fcntl function
#include <unistd.h>
#include <memory>
#include <sstream>

#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/log.h"
#include "utils/tracing_utils.h"

using std::string;

namespace profiler {

const char *kPerfettoExecutable = "perfetto";
const char *kTracedExecutable = "traced";
const char *kTracedProbesExecutable = "traced_probes";
const int kRetryCount = 20;
const int kSleepMsPerRetry = 100;
Perfetto::Perfetto() {}

std::unique_ptr<NonBlockingCommandRunner>
Perfetto::LaunchProcessAndBlockTillStart(const PerfettoArgs &run_args,
                                         const char *process_name,
                                         const char *const env_args[]) {
  string process_path = GetPath(process_name, run_args.abi_arch);
  const char *process_args[] = {process_path.c_str(), nullptr};
  std::unique_ptr<NonBlockingCommandRunner> runner(
      new NonBlockingCommandRunner(process_path, true));
  runner->Run(process_args, string(), env_args);
  if (!runner->BlockUntilChildprocessExec()) {
    runner->Kill();
  }
  return runner;
}

Perfetto::LaunchStatus Perfetto::Run(const PerfettoArgs &run_args) {
  std::string lib_path =
      string("LD_LIBRARY_PATH=") + CurrentProcess::dir() + run_args.abi_arch;
  const char *env_args[] = {
      "PERFETTO_CONSUMER_SOCK_NAME=@perfetto_perfd_profiler_consumer",
      "PERFETTO_PRODUCER_SOCK_NAME=@perfetto_perfd_profiler_producer",
      // Path to libperfetto.so
      lib_path.c_str(), NULL};
  LaunchStatus launch_status = LAUNCH_STATUS_SUCCESS;
  // Run traced before running the probes as this is the server
  // and traced_probes is the cilent. The server host the data and
  // the client collects the data.
  if (traced_.get() == nullptr || !traced_->IsRunning()) {
    traced_ =
        LaunchProcessAndBlockTillStart(run_args, kTracedExecutable, env_args);
    if (!traced_->IsRunning()) {
      launch_status |= FAILED_LAUNCH_TRACED;
    }
  }

  if (traced_probes_.get() == nullptr || !traced_probes_->IsRunning()) {
    traced_probes_ = LaunchProcessAndBlockTillStart(
        run_args, kTracedProbesExecutable, env_args);
    if (!traced_probes_->IsRunning()) {
      launch_status |= FAILED_LAUNCH_TRACED_PROBES;
    }
  }

  // Run perfetto as the interface to configure the traced and traced_probes
  // Perfetto allows us to turn on and off tracing as well as configure
  // what gets traced, how, and where it gets saved to.
  string perfettoPath = GetPath(kPerfettoExecutable, run_args.abi_arch);
  command_ = std::unique_ptr<NonBlockingCommandRunner>(
      new NonBlockingCommandRunner(perfettoPath, true));
  // Serialize the config as a binary proto.
  std::ostringstream binary_config;
  run_args.config.SerializeToOstream(&binary_config);
  // execve requires the argument array to end with a null value.
  // -c - tells perfetto to expect the config to be passed in via STDIN.
  // Note: With the side loading of perfetto we no longer need to pass
  // the config in via the stdin. However since this is currently the way
  // we launch/communicate with perfetto there is little need to change it.
  // The alternative is chagne "-c -" to "-c /path/to/config"
  const char *args[] = {perfettoPath.c_str(),
                        "-c",
                        "-",
                        "-o",
                        run_args.output_file_path.c_str(),
                        nullptr};

  command_->Run(args, binary_config.str(), env_args);

  // A sleep is needed to block until perfetto can start tracer.
  // Sometimes this can fail in the event it fails its better to
  // inform the user ASAP instead of when the trace is stopped.
  WaitForTracerStatus(true);

  if (!command_->IsRunning()) {
    launch_status |= FAILED_LAUNCH_PERFETTO;
  }
  if (!IsTracerRunning()) {
    Stop();
    launch_status |= FAILED_LAUNCH_TRACER;
  }
  return launch_status;
}

void Perfetto::Stop() {
  if (IsPerfettoRunning()) {
    command_->Kill();
    command_.release();
  }

  if (IsTracerRunning()) {
    // Attempt to stop tracer since we know it is our process that opened it.
    // This helps guard against perfetto failing to close the tracing pipe.
    // If the pipe is not closed then the user is unable to run perfett/atrace
    // until they reboot the phone or close the pipe manually via the shell.
    ForceStopTracer();
  }
  // Sometimes stopping (even when forced) isn't instant. As such we wait and
  // let the system clean up.
  // Perfetto manager will check the status of the capture and report in the
  // event this timesout.
  WaitForTracerStatus(false);
}

void Perfetto::WaitForTracerStatus(bool expected_tracer_running) {
  for (int i = 0; i < kRetryCount && expected_tracer_running != IsTracerRunning();
       i++) {
    usleep(Clock::ms_to_us(kSleepMsPerRetry));
  }
}

void Perfetto::Shutdown() {
  Stop();
  if (traced_probes_.get() != nullptr && traced_probes_->IsRunning()) {
    traced_probes_->Kill();
    traced_probes_.release();
  }
  if (traced_.get() != nullptr && traced_->IsRunning()) {
    traced_->Kill();
    traced_.release();
  }
}

string Perfetto::GetPath(const char *executable, const string &abi_arch) const {
  std::ostringstream path;
  path << CurrentProcess::dir();
  path << executable << "_" << abi_arch;
  return path.str();
}

bool Perfetto::IsPerfettoRunning() {
  return command_.get() != nullptr && command_->IsRunning();
}

bool Perfetto::IsTracerRunning() { return TracingUtils::IsTracerRunning(); }

void Perfetto::ForceStopTracer() { TracingUtils::ForceStopTracer(); }

}  // namespace profiler
