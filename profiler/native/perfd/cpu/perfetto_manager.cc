/*
 * Copyright (C) 2009 The Android Open Source Project
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
#include "perfetto_manager.h"
#include "perfetto.h"

#include "proto/profiler.grpc.pb.h"
#include "utils/log.h"
#include "utils/trace.h"

using profiler::proto::Device;
using std::string;

namespace profiler {

// TODO (b/126401684): Change the trace file location when sideloading perfetto.
const char* PerfettoManager::kPerfettoTraceFile =
    "/data/misc/perfetto-traces/trace";

PerfettoManager::PerfettoManager(std::shared_ptr<Perfetto> perfetto)
    : perfetto_(std::move(perfetto)), is_profiling_(false) {}

bool PerfettoManager::StartProfiling(
    const perfetto::protos::TraceConfig& config,
    std::string* trace_path,
    std::string* error) {
  if (is_profiling_) {
    return false;
  }
  Trace trace("CPU: StartProfiling perfetto");
  // Point trace path to entry's trace path so the trace can be pulled later.
  *trace_path = kPerfettoTraceFile;
  perfetto_->Run({config, kPerfettoTraceFile});
  is_profiling_ = perfetto_->IsPerfettoRunning();
  return is_profiling_;
}

bool PerfettoManager::StopProfiling(std::string* error) {
  Trace trace("CPU:StopProfiling perfetto");
  Shutdown();
  return !is_profiling_;
}

void PerfettoManager::Shutdown() {
  Trace trace("CPU:Shutdown perfetto");
  if (is_profiling_) {
    perfetto_->Stop();
    is_profiling_ = perfetto_->IsPerfettoRunning();
  }
}

perfetto::protos::TraceConfig PerfettoManager::BuildConfig(
    string app_pkg_name, int buffer_size_in_kb) {
  perfetto::protos::TraceConfig config;
  config.set_write_into_file(true);
  config.set_file_write_period_ms(1000);

  auto* buffer = config.add_buffers();
  buffer->set_size_kb(buffer_size_in_kb);
  auto* source = config.add_data_sources();
  auto* data_config = source->mutable_config();
  data_config->set_name("linux.ftrace");
  auto* ftrace_config = data_config->mutable_ftrace_config();
  ftrace_config->set_buffer_size_kb(4096);
  ftrace_config->set_drain_period_ms(250);
  ftrace_config->add_ftrace_events("print");
  ftrace_config->add_atrace_categories("gfx");
  ftrace_config->add_atrace_categories("input");
  ftrace_config->add_atrace_categories("view");
  ftrace_config->add_atrace_categories("wm");
  ftrace_config->add_atrace_categories("am");
  ftrace_config->add_atrace_categories("sm");
  ftrace_config->add_atrace_categories("camera");
  ftrace_config->add_atrace_categories("hal");
  ftrace_config->add_atrace_categories("res");
  ftrace_config->add_atrace_categories("pm");
  ftrace_config->add_atrace_categories("sched");
  ftrace_config->add_atrace_categories("freq");
  ftrace_config->add_atrace_apps("*");

  // TODO: Enable this in the future when we want to capture logcat output.
  //source = config.add_data_sources();
  //data_config = source->mutable_config();
  //data_config->set_name("android.log");
  //auto* log = data_config->mutable_android_log_config();

  // Add config to get process and thread names.
  // This is required to properly parse perfetto captures with trebuchet.
  source = config.add_data_sources();
  data_config = source->mutable_config();
  data_config->set_name("linux.process_stats");
  auto* proc = data_config->mutable_process_stats_config();
  proc->set_scan_all_processes_on_start(true);
  proc->set_record_thread_names(true);
  proc->set_proc_stats_poll_ms(1000);

  return config;
}

}  // namespace profiler
