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

#ifndef PERFD_CPU_PERFETTO_MANAGER_H_
#define PERFD_CPU_PERFETTO_MANAGER_H_

#include "perfd/common/perfetto.h"
#include "proto/cpu.grpc.pb.h"
#include "protos/perfetto/config/perfetto_config.grpc.pb.h"
#include "utils/fs/file_system.h"

namespace profiler {

// Class to manage running perfetto and defining the output path for traces.
// This Perfetto class passed in is an abstraction of the perfetto process
// that gets run when a recording is started. This abstraction is setup to
// allow independent testing of starting perfetto recordings without needing
// a device, or the fake android framework.
class PerfettoManager {
 public:
  PerfettoManager()
      : PerfettoManager(std::shared_ptr<Perfetto>(new Perfetto())) {}
  explicit PerfettoManager(std::shared_ptr<Perfetto> perfetto);
  virtual ~PerfettoManager() = default;

  // Buids a default perfetto config. The default config creates a memory buffer
  // of size |acquired_buffer_size_kb|. This buffer gets flushed to disk at
  // regular intervals. This config does not specify a maximum recording size or
  // length. The |app_pkg_name| is used to tell atrace to capture
  // tracing_mark_write events from the specified app. The
  // |acquired_buffer_size_size_kb| is to specify the
  static perfetto::protos::TraceConfig BuildFtraceConfig(
      std::string app_pkg_name, int acquired_buffer_size_kb);

  // Builds a default heapprofd config for perfetto. The default config creates
  // a fixed size memory buffer of 8MB. This buffer gets flushed to disk at
  // regular intervals. This config does not specify a maximum recording size or
  // time. Either |sampling_interval_bytes| or |continuous_dump_interval| are
  // required for the config to capture any memory. For more information about
  // the arguments see https://docs.perfetto.dev/#/heapprofd.
  static perfetto::protos::TraceConfig BuildHeapprofdConfig(
      std::string app_pkg_name_or_pid, int sampling_interval_bytes,
      int continuous_dump_interval_ms, int shared_memory_buffer_bytes);

  // Returns true if profiling was started successfully.
  // |trace_path| is also set to where the trace file will be made available
  // once profiling of this app is stopped. To call this method on an already
  // profiled app is a noop and returns false.
  // Only one instance of Perfetto should be running at a time.
  // Mark it virtual to make it easy to mock for testing.
  virtual bool StartProfiling(const std::string &app_name,
                              const std::string &abi_arch,
                              const perfetto::protos::TraceConfig &config,
                              const std::string &trace_path,
                              std::string *error);

  // Stops profiling returns true if perfetto is no longer running.
  profiler::proto::TraceStopStatus::Status StopProfiling(std::string *error);

  bool IsProfiling() {
    return perfetto_->IsPerfettoRunning() || perfetto_->IsTracerRunning();
  }

  // Stops the perfetto process from running. Called when perfd dies.
  void Shutdown();

 private:
  std::shared_ptr<Perfetto> perfetto_;

  // Helper function to setup common variables between various perfetto configs.
  static perfetto::protos::TraceConfig BuildCommonTraceConfig();
};
}  // namespace profiler

#endif  // PERFD_CPU_PERFETTO_MANAGER_H_
