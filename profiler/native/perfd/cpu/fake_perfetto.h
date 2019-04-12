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
      : perfetto_state_(false),
        tracer_state_(false),
        perfetto_run_state_(true),
        tracer_run_state_(true),
        shutdown_(false) {}
  ~FakePerfetto() override {}

  void Run(const PerfettoArgs& run_args) override {
    perfetto_state_ = perfetto_run_state_;
    tracer_state_ = tracer_run_state_;
    abi_arch_ = run_args.abi_arch;
    output_file_path_ = run_args.output_file_path;
    config_ = run_args.config;
  }
  bool IsPerfettoRunning() override { return perfetto_state_; }
  bool IsTracerRunning() override { return tracer_state_; }
  void Stop() override {
    perfetto_state_ = false;
    tracer_state_ = false;
  }
  void Shutdown() override { Stop(); shutdown_ = true; }
  bool IsShutdown() { return shutdown_; }

  const std::string& OutputFilePath() { return output_file_path_; }
  const std::string& AbiArch() { return abi_arch_; }
  const perfetto::protos::TraceConfig& Config() { return config_; }
  void SetTracerState(bool state) { tracer_state_ = state; }
  void SetPerfettoState(bool state) { perfetto_state_ = state; }
  void SetRunStateTo(bool perfetto, bool tracer) {
    perfetto_run_state_ = perfetto;
    tracer_run_state_ = tracer;
  }

 private:
  bool perfetto_state_;
  bool tracer_state_;
  bool tracer_run_state_;
  bool perfetto_run_state_;
  bool shutdown_;
  perfetto::protos::TraceConfig config_;
  std::string output_file_path_;
  std::string abi_arch_;
};

}  // namespace profiler

#endif  // PERFD_CPU_FAKE_PERFETTO_H_
