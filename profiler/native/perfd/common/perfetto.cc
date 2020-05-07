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

#include <fcntl.h>  // library for fcntl function
#include <unistd.h>

#include <memory>
#include <sstream>

#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/tracing_utils.h"

using std::string;

namespace profiler {

const char *kPerfettoExecutable = "perfetto";
const char *kSystemPerfettoExecutable = "/system/bin/perfetto";
const char *kTracedExecutable = "traced";
const char *kTracedProbesExecutable = "traced_probes";
const char *kFixedPerfettoTracePath = "/data/misc/perfetto-traces/";
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
      lib_path.c_str(), nullptr};
  LaunchStatus launch_status = LAUNCH_STATUS_SUCCESS;
  string perfettoPath;
  expected_output_path_ = run_args.output_file_path;

  // For older than Q we sideload perfetto.
  bool run_sideload_perfetto = DeviceInfo::feature_level() < DeviceInfo::Q;
  if (run_sideload_perfetto) {
    // Run traced before running the probes as this is the server
    // and traced_probes is the client. The server host the data and
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
    perfettoPath = GetPath(kPerfettoExecutable, run_args.abi_arch);
    perfetto_trace_path_ = run_args.output_file_path;
  } else {
    // When running built in perfetto we need to enable traced. This is enabled
    // by default on pixel devices but not other OEMS.
    if (!EnableProfiling()) {
      return FAILED_LAUNCH_TRACED;
    }
    // Perfetto only has write access to the |kFixedPerfettoTracePath| folder.
    // As such we take the expected file name and tell perfetto to write to a
    // file with that name in the |kFixedPerfettoTracePath| folder. For Q this
    // folder is readonly by shell, for R+ this folder is read/delete.
    perfettoPath = string(kSystemPerfettoExecutable);
    perfetto_trace_path_ = string(kFixedPerfettoTracePath);
    // Find the filename of the expected output file and use that
    // for the filename of the /data/misc/perfetto-traces/ file.
    size_t last_slash = run_args.output_file_path.find_last_of("/");
    if (last_slash != std::string::npos) {
      perfetto_trace_path_.append(run_args.output_file_path.substr(last_slash));
    }
  }

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
  const char *args[] = {perfettoPath.c_str(),         "-c",   "-", "-o",
                        perfetto_trace_path_.c_str(), nullptr};

  // If we sideload perfetto we need to tell it how to connect to the probes
  // socks if we are not sideloading perfetto passing this information will
  // cause errors.
  command_->Run(args, binary_config.str(),
                run_sideload_perfetto ? env_args : nullptr);
  // A sleep is needed to block until perfetto can start tracer.
  // Sometimes this can fail in the event it fails its better to
  // inform the user ASAP instead of when the trace is stopped.
  if (run_sideload_perfetto) {
    WaitForTracerStatus(true);
  }

  if (!command_->IsRunning()) {
    launch_status |= FAILED_LAUNCH_PERFETTO;
  }
  if (run_sideload_perfetto && !IsTracerRunning()) {
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

  if (perfetto_trace_path_.compare(expected_output_path_) != 0) {
    // For Q+ we don't use the sideloaded perfetto but the one built into the
    // OS. The directory that the OS built-in perfetto copies traces to is
    // readonly as such we copy the file to the expected output path so the
    // rest of the pipeline can continue normally. This primarily acts as a
    // compatibility with the other cpu tracing.
    DiskFileSystem disk;
    if (disk.HasFile(perfetto_trace_path_)) {
      disk.CopyFile(perfetto_trace_path_, expected_output_path_);
      // Attempt to clean up after the copy.
      // After QQ2A.191031.001, shell has delete access to the folder so this
      // will work. For early Q builds, it will just fail silently.
      disk.DeleteFile(perfetto_trace_path_);
    }
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
  for (int i = 0;
       i < kRetryCount && expected_tracer_running != IsTracerRunning(); i++) {
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
