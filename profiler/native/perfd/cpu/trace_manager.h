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
#ifndef PERFD_CPU_TRACE_MANAGER_H_
#define PERFD_CPU_TRACE_MANAGER_H_

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "perfd/common/perfetto_manager.h"
#include "perfd/cpu/atrace_manager.h"
#include "perfd/cpu/profiling_app.h"
#include "perfd/cpu/simpleperf.h"
#include "perfd/cpu/simpleperf_manager.h"
#include "proto/cpu.grpc.pb.h"
#include "proto/transport.grpc.pb.h"
#include "utils/activity_manager.h"
#include "utils/clock.h"
#include "utils/count_down_latch.h"
#include "utils/fs/disk_file_system.h"
#include "utils/termination_service.h"
#include "utils/time_value_buffer.h"

namespace profiler {

// A helper class for managing start/stop of various traces and keeping track
// of their records.
class TraceManager final {
 public:
  static const int kAtraceBufferSizeInMb = 32;
  static const int kPerfettoBufferSizeInMb = 4;

  TraceManager(Clock* clock,
               const profiler::proto::DaemonConfig::CpuConfig& cpu_config,
               TerminationService* termination_service)
      : TraceManager(
            clock, cpu_config, termination_service, ActivityManager::Instance(),
            std::unique_ptr<SimpleperfManager>(new SimpleperfManager()),
            // Number of millis to wait between atrace dumps when profiling.
            // The average user will run a capture around 20 seconds, however to
            // support longer captures we should dump the data (causing a
            // hitch). This data dump enables us to have long captures.
            std::unique_ptr<AtraceManager>(new AtraceManager(
                std::unique_ptr<FileSystem>(new DiskFileSystem()), clock,
                AtraceManager::kDefaultDumpDataInternalMs)),
            std::unique_ptr<PerfettoManager>(new PerfettoManager())) {}

  TraceManager(Clock* clock,
               const profiler::proto::DaemonConfig::CpuConfig& cpu_config,
               TerminationService* termination_service,
               ActivityManager* activity_manager,
               std::unique_ptr<SimpleperfManager> simpleperf_manager,
               std::unique_ptr<AtraceManager> atrace_manager,
               std::unique_ptr<PerfettoManager> perfetto_manager)
      : clock_(clock),
        cpu_config_(cpu_config),
        activity_manager_(activity_manager),
        simpleperf_manager_(std::move(simpleperf_manager)),
        atrace_manager_(std::move(atrace_manager)),
        perfetto_manager_(std::move(perfetto_manager)) {
    termination_service->RegisterShutdownCallback([this](int signal) {
      this->activity_manager_->Shutdown();
      this->simpleperf_manager_->Shutdown();
      this->atrace_manager_->Shutdown();
      this->perfetto_manager_->Shutdown();
    });
  }

  // Request to start tracing. It returns the cached ProfilingApp if the trace
  // started successfully (e.g. if there are no ongoing trace for the specified
  // app), nullptr otherwise.
  //
  // Note that the caller is required to specify the |request_timestamp_ns|
  // which will be used to indicate the start time of the trace. For all
  // non-API-initiated tracing, this should be the time when the daemon receives
  // the start trace request. For API-initiated tracing, the timestamp
  // originates from the app agent. Also for API-initiated tracing, the trace
  // logic is handled via the app, so this method will only log and generate
  // the |ProfilingApp| record without calling any trace commands.
  ProfilingApp* StartProfiling(
      int64_t request_timestamp_ns,
      const proto::CpuTraceConfiguration& configuration,
      proto::TraceStartStatus* status);

  // Request to stop an ongoing trace. Returns the cached ProfilingApp with
  // the end timestamp marked if there is an existing trace, nullptr otherwise.
  // Note that the caller is responsible for parsing/reading the trace outputs
  // that should be generated in the returned ProfilingApp's configuration's
  // trace path.
  //
  // TODO: currently we only support one ongoing capture per app, we should
  // look into supporting different types of captures simultaneously. e.g.
  // simpleperf while doing atrace, so users can correlate callstacks.
  // TODO: this current does not validate whether we are stopping a specific
  // trace (e.g. it stops any ongoing trace), the more correct logic would be
  // to pass in a |CpuTraceConfiguration| and validate we are stopping the
  // correct one.
  ProfilingApp* StopProfiling(int64_t request_timestamp_ns,
                              const std::string& app_name, bool need_trace,
                              proto::TraceStopStatus* status);

  // Returns the |ProfilingApp| of an app if there is an ongoing tracing, null
  // otherwise.
  ProfilingApp* GetOngoingCapture(const std::string& app_name);

  // Returns the captures from process of |pid| that overlap with the given
  // interval [|from|, |to|], both inclusive.
  std::vector<ProfilingApp> GetCaptures(const std::string& app_name,
                                        int64_t from, int64_t to);

  // Visible for testing.
  SimpleperfManager* simpleperf_manager() { return simpleperf_manager_.get(); }

  // Visible for testing.
  AtraceManager* atrace_manager() { return atrace_manager_.get(); }

  PerfettoManager* perfetto_manager() { return perfetto_manager_.get(); }

 private:
  Clock* clock_;
  const proto::DaemonConfig::CpuConfig cpu_config_;
  ActivityManager* activity_manager_;
  std::unique_ptr<SimpleperfManager> simpleperf_manager_;
  std::unique_ptr<AtraceManager> atrace_manager_;
  std::unique_ptr<PerfettoManager> perfetto_manager_;

  std::recursive_mutex capture_mutex_;

  // Map from app package name to the corresponding data of ongoing captures.
  std::map<std::string, CircularBuffer<ProfilingApp>> capture_cache_;
};

}  // namespace profiler

#endif  // PERFD_CPU_TRACE_MANAGER_H_
