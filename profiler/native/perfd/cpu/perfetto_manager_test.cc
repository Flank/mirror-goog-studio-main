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
#include "perfetto_manager.h"
#include "fake_perfetto.h"
#include "utils/fake_clock.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

using std::string;
namespace profiler {

TEST(PerfettoManagerTest, ProfilingStartStop) {
  FakeClock fake_clock;
  std::shared_ptr<Perfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{&fake_clock, perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  EXPECT_TRUE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_TRUE(perfetto->IsPerfettoRunning());
  EXPECT_TRUE(perfetto->IsTracerRunning());
  EXPECT_TRUE(manager.StopProfiling(&error));
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
}

TEST(PerfettoManagerTest, ValidateRunArgs) {
  FakeClock fake_clock;
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{&fake_clock, perfetto};
  const char* app_name = "App Name";
  perfetto::protos::TraceConfig config =
      PerfettoManager::BuildConfig(app_name, 32000);
  string trace_path;
  string error;
  const char* abi_arch = "armv8";
  EXPECT_TRUE(
      manager.StartProfiling(app_name, abi_arch, config, &trace_path, &error));
  EXPECT_TRUE(perfetto->IsPerfettoRunning());
  EXPECT_EQ(perfetto->AbiArch(), abi_arch);
  // Cannot EXPECT_EQ two protos as the operator == fails.
  string in_config;
  config.SerializeToString(&in_config);
  string out_config;
  perfetto->Config().SerializeToString(&out_config);
  EXPECT_EQ(in_config, out_config);
  EXPECT_EQ(perfetto->OutputFilePath(), trace_path);
}

TEST(PerfettoManagerTest, ValidateShutdown) {
  FakeClock fake_clock;
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{&fake_clock, perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  const char* abi_arch = "armv8";
  const char* app_name = "App Name";
  EXPECT_TRUE(
      manager.StartProfiling(app_name, abi_arch, config, &trace_path, &error));
  EXPECT_TRUE(perfetto->IsPerfettoRunning());
  EXPECT_TRUE(manager.IsProfiling());
  manager.Shutdown();
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
  EXPECT_FALSE(manager.IsProfiling());
  EXPECT_TRUE(perfetto->IsShutdown());
}

TEST(PerfettoManagerTest, ValidateConfig) {
  const char* app_name = "App Name";
  const int buffer_size_kb = 32000;
  perfetto::protos::TraceConfig config =
      PerfettoManager::BuildConfig(app_name, buffer_size_kb);
  // Assume the format of the config, perfetto doesn't care about the order but
  // for the test we assume its order so we don't need to search for data.
  const auto& ftrace_config = config.data_sources()[0].config().ftrace_config();
  EXPECT_EQ(ftrace_config.atrace_apps()[0], "*");
  // The minimal set of atrace categories needed is sched.
  const char* expected_atrace_categories[] = {"sched"};
  const int categories_size = sizeof(expected_atrace_categories) /
                              sizeof(expected_atrace_categories[0]);
  int categories_found = 0;
  for (const auto& category : ftrace_config.atrace_categories()) {
    for (int i = 0; i < categories_size; i++) {
      if (category.compare(expected_atrace_categories[i]) == 0) {
        categories_found++;
      }
    }
  }
  EXPECT_EQ(categories_found, categories_size);
  EXPECT_EQ(config.buffers()[0].size_kb(), buffer_size_kb);
}

TEST(PerfettoManagerTest, ValidateShutdownErrors) {
  FakeClock fake_clock;
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  perfetto->SetRunStateTo(true, true);
  PerfettoManager manager{&fake_clock, perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;

  // Test failing to stop tracer.
  perfetto->SetStopStateTo(false, true);
  EXPECT_TRUE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_EQ(
      manager.StopProfiling(&error),
      profiler::proto::CpuProfilingAppStopResponse::STILL_PROFILING_AFTER_STOP);
  EXPECT_EQ(error, "Failed to stop tracer.");

  // Clear state and test failing to stop perfetto.
  error = "";
  perfetto->SetStopStateTo(true, false);
  EXPECT_EQ(
      manager.StopProfiling(&error),
      profiler::proto::CpuProfilingAppStopResponse::STILL_PROFILING_AFTER_STOP);
  EXPECT_EQ(error, "Failed to stop perfetto.");
}

TEST(PerfettoManagerTest, ValidateErrorsToRun) {
  FakeClock fake_clock;
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  perfetto->SetRunStateTo(false, true);
  PerfettoManager manager{&fake_clock, perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  // Fail to launch perfetto
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
  EXPECT_EQ(error, "Failed to launch perfetto.");

  // Fail to launch tracer
  perfetto->SetRunStateTo(true, false);
  perfetto->SetPerfettoState(false);
  perfetto->SetTracerState(false);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_EQ(error, "Failed to launch tracer.");

  // Attempt to record with tracer already running.
  perfetto->SetRunStateTo(true, true);
  perfetto->SetPerfettoState(false);
  perfetto->SetTracerState(true);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_EQ(error, "Tracer is already running unable to run perfetto.");

  // Attempt to record with perfetto already running.
  perfetto->SetRunStateTo(true, true);
  perfetto->SetPerfettoState(true);
  perfetto->SetTracerState(false);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, &trace_path, &error));
  EXPECT_EQ(error, "Perfetto is already running unable to start new trace.");
}

}  // namespace profiler