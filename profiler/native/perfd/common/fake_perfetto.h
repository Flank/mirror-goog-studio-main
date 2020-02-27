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

#ifndef PERFD_CPU_FAKE_PERFETTO_H_
#define PERFD_CPU_FAKE_PERFETTO_H_

#include "perfetto.h"

namespace profiler {

// A subclass of Perfetto to be used in tests. The class maintains a simple
// state of if perfetto is assumed to be running or not.
class FakePerfetto : public Perfetto {
 public:
  explicit FakePerfetto()
      : tracer_state_(false),
        tracer_run_state_(true),
        tracer_stop_state_(false),
        perfetto_state_(false),
        perfetto_run_state_(true),
        perfetto_stop_state_(false),
        shutdown_(false) {}
  ~FakePerfetto() override {}

  Perfetto::LaunchStatus Run(const PerfettoArgs& run_args) override {
    perfetto_state_ = perfetto_run_state_;
    tracer_state_ = tracer_run_state_;
    abi_arch_ = run_args.abi_arch;
    output_file_path_ = run_args.output_file_path;
    config_ = run_args.config;
    Perfetto::LaunchStatus launch_status = LAUNCH_STATUS_SUCCESS;
    if (!tracer_state_) {
      launch_status |= FAILED_LAUNCH_TRACER;
    }
    if (!perfetto_state_) {
      launch_status |= FAILED_LAUNCH_PERFETTO;
    }
    return launch_status;
  }
  bool IsPerfettoRunning() override { return perfetto_state_; }
  bool IsTracerRunning() override { return tracer_state_; }
  void Stop() override {
    perfetto_state_ = perfetto_stop_state_;
    tracer_state_ = tracer_stop_state_;
  }
  void Shutdown() override {
    Stop();
    shutdown_ = true;
  }
  bool IsShutdown() { return shutdown_; }
  void ForceStopTracer() override { tracer_state_ = false; }

  const std::string& OutputFilePath() { return output_file_path_; }
  const std::string& AbiArch() { return abi_arch_; }
  const perfetto::protos::TraceConfig& Config() { return config_; }
  void SetTracerState(bool state) { tracer_state_ = state; }
  void SetPerfettoState(bool state) { perfetto_state_ = state; }
  void SetRunStateTo(bool perfetto, bool tracer) {
    perfetto_run_state_ = perfetto;
    tracer_run_state_ = tracer;
  }
  void SetStopStateTo(bool perfetto, bool tracer) {
    perfetto_stop_state_ = perfetto;
    tracer_stop_state_ = tracer;
  }

 private:
  // Holds the current state of tracer
  bool tracer_state_;
  // Holds the state to put tracer in when run is called.
  // This allows us to fail running tracer.
  bool tracer_run_state_;
  // Holds the state to put tracer in when stop is called.
  // This allows us to fail stopping tracer.
  bool tracer_stop_state_;
  // Holds the current state of perfetto.
  bool perfetto_state_;
  // Holds the state to put perfetto in when run is called.
  bool perfetto_run_state_;
  // Holds the state to put perfetto in when stop is called.
  bool perfetto_stop_state_;
  // Allows us to test if shutdown is called.
  bool shutdown_;
  perfetto::protos::TraceConfig config_;
  std::string output_file_path_;
  std::string abi_arch_;
};

}  // namespace profiler

#endif  // PERFD_CPU_FAKE_PERFETTO_H_
