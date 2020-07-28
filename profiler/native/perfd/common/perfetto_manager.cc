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
#include "utils/trace.h"

using profiler::proto::Device;
using profiler::proto::TraceStopStatus;
using std::string;

namespace profiler {

PerfettoManager::PerfettoManager(std::shared_ptr<Perfetto> perfetto)
    : perfetto_(std::move(perfetto)) {}

bool PerfettoManager::StartProfiling(
    const std::string& app_name, const std::string& abi_arch,
    const perfetto::protos::TraceConfig& config, const std::string& trace_path,
    std::string* error) {
  if (perfetto_->IsPerfettoRunning()) {
    error->append("Perfetto is already running unable to start new trace.");
    return false;
  }
  if (perfetto_->IsTracerRunning()) {
    error->append("Tracer is already running unable to run perfetto.");
    return false;
  }
  Trace trace("CPU: StartProfiling perfetto");
  Perfetto::LaunchStatus status =
      perfetto_->Run({config, abi_arch, trace_path});
  if ((status & Perfetto::FAILED_LAUNCH_PERFETTO) != 0) {
    error->append("Failed to launch perfetto.\n");
  }
  if ((status & Perfetto::FAILED_LAUNCH_TRACER) != 0) {
    error->append("Failed to launch tracer.\n");
  }
  if ((status & Perfetto::FAILED_LAUNCH_TRACED) != 0) {
    error->append("Failed to launch traced.\n");
  }
  if ((status & Perfetto::FAILED_LAUNCH_TRACED_PROBES) != 0) {
    error->append("Failed to launch traced_probes.");
  }
  return status == Perfetto::LAUNCH_STATUS_SUCCESS;
}

TraceStopStatus::Status PerfettoManager::StopProfiling(std::string* error) {
  Trace trace("CPU:StopProfiling perfetto");
  perfetto_->Stop();
  bool stop_succeeded = true;
  if (perfetto_->IsTracerRunning()) {
    error->append("Failed to stop tracer.");
    stop_succeeded = false;
  }
  if (perfetto_->IsPerfettoRunning()) {
    error->append("Failed to stop perfetto.");
    stop_succeeded = false;
  }
  return stop_succeeded ? TraceStopStatus::SUCCESS
                        : TraceStopStatus::STILL_PROFILING_AFTER_STOP;
}

void PerfettoManager::Shutdown() {
  Trace trace("CPU:Shutdown perfetto");
  if (IsProfiling()) {
    perfetto_->Shutdown();
  }
}

perfetto::protos::TraceConfig PerfettoManager::BuildCommonTraceConfig() {
  perfetto::protos::TraceConfig config;
  config.set_write_into_file(true);
  // The intervals are somewhat arbitrary. We want to write to file
  // at an interval that isn't going to be super noticable for users
  // at the same time we don't want to fill our in memory buffers.
  // We do not need to set the flush period since we are not doing
  // realtime streaming of data. Setting the flush period foces all
  // probes to flush any in memory data to disk. This can cause
  // significant latency and have a negative impact on the data
  // and the users app.
  config.set_file_write_period_ms(250);
  return config;
}

perfetto::protos::TraceConfig PerfettoManager::BuildHeapprofdConfig(
    std::string app_pkg_name_or_pid, int sampling_interval_bytes,
    int continuous_dump_interval_ms, int shared_memory_buffer_bytes) {
  perfetto::protos::TraceConfig config = BuildCommonTraceConfig();
  auto* buffer = config.add_buffers();
  // Set an arbitrary buffer size that is not unreasonable to request, and
  // allows us a reasonable size  to not overflow.
  buffer->set_size_kb(8192);
  auto* source = config.add_data_sources();
  auto* data_config = source->mutable_config();
  data_config->set_name("android.heapprofd");
  auto* heap_config = data_config->mutable_heapprofd_config();
  heap_config->set_sampling_interval_bytes(sampling_interval_bytes);
  heap_config->add_process_cmdline(app_pkg_name_or_pid.c_str());
  heap_config->set_shmem_size_bytes(shared_memory_buffer_bytes);
  heap_config->mutable_continuous_dump_config()->set_dump_interval_ms(
      continuous_dump_interval_ms);
  return config;
}

perfetto::protos::TraceConfig PerfettoManager::BuildFtraceConfig(
    string app_pkg_name, int buffer_size_in_kb) {
  // The current settings when profiled on a Pixel 2 account for an overhead
  // of approximately 50% CPU time on a little core. This means there is a
  // total performance overhead of ~6%.
  perfetto::protos::TraceConfig config = BuildCommonTraceConfig();
  auto* buffer = config.add_buffers();
  buffer->set_size_kb(buffer_size_in_kb);
  auto* source = config.add_data_sources();
  auto* data_config = source->mutable_config();
  data_config->set_name("linux.ftrace");
  auto* ftrace_config = data_config->mutable_ftrace_config();
  ftrace_config->set_buffer_size_kb(buffer_size_in_kb);
  // Drain ftrace every 10frames @ 60fps
  ftrace_config->set_drain_period_ms(170);
  // Enable more counters
  ftrace_config->add_ftrace_events("thermal/thermal_temperature");
  ftrace_config->add_ftrace_events("perf_trace_counters/perf_trace_user");
  // If this event is reported by the OS. Fenced events will help users track
  // synchronization issues. Most commonly fences are used to guard buffers
  // used by kernel level drivers (eg. GPU). They are captured when the driver
  // needs to do work and they are signaled when the driver is done.
  ftrace_config->add_ftrace_events("fence/signaled");
  ftrace_config->add_ftrace_events("fence/fence_wait_start");

  // Enable task tracking
  // This enables us to capture events/metadata that helps to track
  // processes/threads as they get renamed/spawned. Reduces the number of
  // processes/threads with only PIDs and no name attached to them in the
  // capture.
  ftrace_config->add_ftrace_events("task/task_rename");
  ftrace_config->add_ftrace_events("task/task_newtask");

  // Standard set of atrace categories
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
  ftrace_config->add_atrace_categories("ss");
  ftrace_config->add_atrace_categories("power");
  ftrace_config->add_atrace_categories("database");
  ftrace_config->add_atrace_categories("binder_driver");
  ftrace_config->add_atrace_categories("binder_lock");

  // Very verbose atrace categories
  ftrace_config->add_atrace_categories("sched");
  ftrace_config->add_atrace_categories("freq");

  // Enable perf counters (mem / oom score / HW VSYNC)
  auto* perf_config = data_config->mutable_perf_event_config();
  perf_config->set_all_cpus(true);  // Required
  perf_config->add_target_cmdline(app_pkg_name);

  // In P and above "*" is supported, if we move to support O we will want to
  // pass in the |app_pkg_name|
  ftrace_config->add_atrace_apps("*");

  // TODO: Enable this in the future when we want to capture logcat output.
  // source = config.add_data_sources();
  // data_config = source->mutable_config();
  // data_config->set_name("android.log");
  // auto* log = data_config->mutable_android_log_config();

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
