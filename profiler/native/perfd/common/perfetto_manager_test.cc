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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "fake_perfetto.h"
#include "utils/fake_clock.h"

using std::string;
namespace profiler {

TEST(PerfettoManagerTest, ProfilingStartStop) {
  std::shared_ptr<Perfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  EXPECT_TRUE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_TRUE(perfetto->IsPerfettoRunning());
  EXPECT_TRUE(perfetto->IsTracerRunning());
  EXPECT_TRUE(manager.StopProfiling(&error));
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
}

TEST(PerfettoManagerTest, ValidateRunArgs) {
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{perfetto};
  const char* app_name = "App Name";
  perfetto::protos::TraceConfig config =
      PerfettoManager::BuildFtraceConfig(app_name, 32000);
  string trace_path;
  string error;
  const char* abi_arch = "armv8";
  EXPECT_TRUE(
      manager.StartProfiling(app_name, abi_arch, config, trace_path, &error));
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
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  const char* abi_arch = "armv8";
  const char* app_name = "App Name";
  EXPECT_TRUE(
      manager.StartProfiling(app_name, abi_arch, config, trace_path, &error));
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
      PerfettoManager::BuildFtraceConfig(app_name, buffer_size_kb);
  EXPECT_EQ(config.data_sources().size(), 5);
  // Assume the format of the config, perfetto doesn't care about the order but
  // for the test we assume its order so we don't need to search for data.

  // Ftrace config
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
  EXPECT_EQ(config.buffers().size(), 2);
  EXPECT_EQ(config.buffers()[0].size_kb(), buffer_size_kb);
  EXPECT_EQ(config.buffers()[1].size_kb(), 256);

  // Process stats
  EXPECT_EQ(config.data_sources()[1].config().name(), "linux.process_stats");
  EXPECT_EQ(config.data_sources()[2].config().name(), "linux.process_stats");
  // CPU information
  EXPECT_EQ(config.data_sources()[3].config().name(), "linux.system_info");
  // Android frame data
  EXPECT_EQ(config.data_sources()[4].config().name(),
            "android.surfaceflinger.frame");
}

TEST(PerfettoManagerTest, ValidateShutdownErrors) {
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  perfetto->SetRunStateTo(true, true);
  PerfettoManager manager{perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;

  // Test failing to stop tracer.
  perfetto->SetStopStateTo(false, true);
  EXPECT_TRUE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_EQ(manager.StopProfiling(&error),
            profiler::proto::TraceStopStatus::STILL_PROFILING_AFTER_STOP);
  EXPECT_EQ(error, "Failed to stop tracer.");

  // Clear state and test failing to stop perfetto.
  error = "";
  perfetto->SetStopStateTo(true, false);
  EXPECT_EQ(manager.StopProfiling(&error),
            profiler::proto::TraceStopStatus::STILL_PROFILING_AFTER_STOP);
  EXPECT_EQ(error, "Failed to stop perfetto.");
}

TEST(PerfettoManagerTest, ValidateErrorsToRun) {
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  perfetto->SetRunStateTo(false, true);
  PerfettoManager manager{perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  // Fail to launch perfetto
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
  EXPECT_EQ(error, "Failed to launch perfetto.\n");

  // Fail to launch tracer
  perfetto->SetRunStateTo(true, false);
  perfetto->SetPerfettoState(false);
  perfetto->SetTracerState(false);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_EQ(error, "Failed to launch tracer.\n");

  // Attempt to record with tracer already running.
  perfetto->SetRunStateTo(true, true);
  perfetto->SetPerfettoState(false);
  perfetto->SetTracerState(true);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_EQ(error, "Tracer is already running unable to run perfetto.");

  // Attempt to record with perfetto already running.
  perfetto->SetRunStateTo(true, true);
  perfetto->SetPerfettoState(true);
  perfetto->SetTracerState(false);
  error = "";
  EXPECT_FALSE(
      manager.StartProfiling("App Name", "armv8", config, trace_path, &error));
  EXPECT_EQ(error, "Perfetto is already running unable to start new trace.");
}

TEST(PerfettoManagerTest, ValidateHeapprofdConfig) {
  const char* app_name = "App.Name";
  int sample_bytes = 1234;
  int shmem_size = 4567;
  int dump_interval = 7890;
  perfetto::protos::TraceConfig config = PerfettoManager::BuildHeapprofdConfig(
      app_name, sample_bytes, dump_interval, shmem_size);
  // Validate we write to file at some interval.
  EXPECT_TRUE(config.write_into_file());
  EXPECT_GT(config.flush_period_ms(), 0);
  EXPECT_GT(config.file_write_period_ms(), 0);
  // Validate we have 1 buffer.
  EXPECT_EQ(config.buffers().size(), 1);
  // Validate heap profd data source.
  auto heap_config = config.data_sources()[0].config().heapprofd_config();
  EXPECT_EQ(heap_config.sampling_interval_bytes(), sample_bytes);
  EXPECT_EQ(heap_config.process_cmdline()[0], app_name);
  EXPECT_EQ(heap_config.shmem_size_bytes(), shmem_size);
  EXPECT_EQ(heap_config.continuous_dump_config().dump_interval_ms(),
            dump_interval);
  EXPECT_TRUE(heap_config.all_heaps());
  EXPECT_TRUE(heap_config.block_client());
}
}  // namespace profiler