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

#include <unistd.h>
#include <memory>

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

void Perfetto::Run(const PerfettoArgs &run_args) {
  const char *env_args[] = {
      "PERFETTO_CONSUMER_SOCK_NAME=@perfetto_perfd_profiler_consumer",
      "PERFETTO_PRODUCER_SOCK_NAME=@perfetto_perfd_profiler_producer",
      // Path to libperfetto.so
      (string("LD_LIBRARY_PATH=") + CurrentProcess::dir() + run_args.abi_arch)
          .c_str(),
      NULL};

  // Run traced before running the probes as this is the server
  // and traced_probes is the cilent. The server host the data and
  // the client collects the data.
  if (traced_.get() == nullptr || !traced_->IsRunning()) {
    string tracedPath = GetPath(kTracedExecutable, run_args.abi_arch);
    const char *tracedArgs[] = {tracedPath.c_str(), nullptr};
    traced_ = std::unique_ptr<NonBlockingCommandRunner>(
        new NonBlockingCommandRunner(tracedPath, true));
    traced_->Run(tracedArgs, string(), env_args);
  }

  if (traced_probes_.get() == nullptr || !traced_probes_->IsRunning()) {
    string tracedProbesPath =
        GetPath(kTracedProbesExecutable, run_args.abi_arch);
    const char *tracedProbeArgs[] = {tracedProbesPath.c_str(), nullptr};
    traced_probes_ = std::unique_ptr<NonBlockingCommandRunner>(
        new NonBlockingCommandRunner(tracedProbesPath, true));
    traced_probes_->Run(tracedProbeArgs, string(), env_args);
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
  for (int i = 0; i < kRetryCount && !IsTracerRunning(); i++) {
    usleep(Clock::ms_to_us(kSleepMsPerRetry));
  }
  if (!IsTracerRunning()) {
    Stop();
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
  return command_.get() != nullptr && command_->IsRunning() &&
         traced_.get() != nullptr && traced_->IsRunning() &&
         traced_probes_.get() != nullptr && traced_probes_->IsRunning();
}

bool Perfetto::IsTracerRunning() { return TracingUtils::IsTracerRunning(); }

}  // namespace profiler
