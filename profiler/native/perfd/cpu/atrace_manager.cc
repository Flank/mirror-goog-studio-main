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
#include "atrace_manager.h"
#include "atrace.h"

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sstream>

#include "proto/profiler.grpc.pb.h"
#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/tokenizer.h"
#include "utils/trace.h"

using std::string;

namespace profiler {

// Number of times we attempt to run the stop atrace command.
const int kRetryStopAttempts = 5;
const int kMbToKb = 1024;
// If Atrace fails to start with our initial requested buffer size, each follow
// up attempt is reduced by this amount. If our buffer is less than 2 times this
// amount we start to divide by 2.
const int kBufferSizeToStepReduceByKb = 8 * kMbToKb;
// Minmum supported buffer size in MB.
const int kBufferMinimumSizeMb = 1;

AtraceManager::AtraceManager(std::unique_ptr<FileSystem> file_system,
                             Clock *clock, int dump_data_interval_ms,
                             std::unique_ptr<Atrace> atrace)
    : file_system_(std::move(file_system)),
      clock_(clock),
      dump_data_interval_ms_(dump_data_interval_ms),
      dumps_created_(0),
      is_profiling_(false),
      atrace_(std::move(atrace)) {}

bool AtraceManager::StartProfiling(const std::string &app_pkg_name,
                                   int sampling_interval_us,
                                   int buffer_size_in_mb,
                                   int *acquired_buffer_size_kb,
                                   std::string *trace_path,
                                   std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  *acquired_buffer_size_kb = 0;
  if (is_profiling_) {
    return false;
  }
  if (buffer_size_in_mb < kBufferMinimumSizeMb) {
    error->append("Requested buffer size is too small");
    return false;
  }

  dumps_created_ = 0;
  Trace trace("CPU: StartProfiling atrace");
  Log::D("Profiler:Received query to profile %s", app_pkg_name.c_str());
  // Build entry to keep track of what is being profiled.
  profiled_app_.trace_path = GetTracePath(app_pkg_name);
  profiled_app_.app_pkg_name = app_pkg_name;
  // Point trace path to entry's trace path so the trace can be pulled later.
  *trace_path = profiled_app_.trace_path;
  // Check if atrace is already running, if it is its okay to use that instance.
  bool isRunning = atrace_->IsAtraceRunning();
  int requested_buffer_size_kb = buffer_size_in_mb * kMbToKb;
  int actual_buffer_size_kb = 0;
  // Retry under the following conditions
  // 1) We have not hit our retry attempts limit.
  // 2) We are not running.
  // 3) Our requested buffer size is greater than our minimum buffer size.
  for (int i = 0; i < kRetryStartAttempts && !isRunning &&
                  requested_buffer_size_kb >= kBufferMinimumSizeMb * kMbToKb;
       i++) {
    std::ostringstream buffer_size_stream;
    buffer_size_stream << "-b " << requested_buffer_size_kb;
    buffer_size_arg_ = buffer_size_stream.str();
    atrace_->Run({app_pkg_name, profiled_app_.trace_path, "--async_start",
                  buffer_size_arg_});
    isRunning = atrace_->IsAtraceRunning();

    // Verify buffer size, if this is not our expected buffersize then cut it in
    // half and try again. This can happen frequently due to the fact that
    // atrace must allocate a contiguous block of memory in the size
    // we are requesting.
    actual_buffer_size_kb = atrace_->GetBufferSizeKb();
    if (actual_buffer_size_kb != requested_buffer_size_kb) {
      // If we can subtract a step from our buffer and still try again do that.
      // Otherwise reduce the buffer in half and try.
      if (requested_buffer_size_kb > 2 * kBufferSizeToStepReduceByKb) {
        requested_buffer_size_kb -= kBufferSizeToStepReduceByKb;
      } else {
        requested_buffer_size_kb /= 2;
      }
      if (isRunning) {
        atrace_->HardStop();
        isRunning = false;
      }
    }
  }

  // This is checked for in the thread below. Setting the
  // value here ensures the thread reads the correct value before executing.
  is_profiling_ = isRunning;
  if (!isRunning) {
    assert(error != nullptr);
    error->append("Failed to run atrace start.");
    if (actual_buffer_size_kb < kBufferMinimumSizeMb) {
      error->append(
          " Atrace could not allocate enough memory to record a trace.");
    }
  } else {
    atrace_thread_ = std::thread(&AtraceManager::DumpData, this);
  }
  *acquired_buffer_size_kb = actual_buffer_size_kb;
  return isRunning;
}

void AtraceManager::DumpData() {
  while (is_profiling_) {
    std::unique_lock<std::mutex> lock(dump_data_mutex_);
    dump_data_condition_.wait_for(
        lock, std::chrono::milliseconds(dump_data_interval_ms_),
        [this] { return !is_profiling_; });
    // Our condition can be woken via time, or stop profiling. If we are no
    // longer profiling we do not need to collect an async_dump as stop
    // profiling will do that for us.
    if (is_profiling_) {
      atrace_->Run({profiled_app_.app_pkg_name, GetNextDumpPath(),
                    "--async_dump", buffer_size_arg_});
    }
  }
}

string AtraceManager::GetTracePath(const string &app_name) const {
  std::ostringstream path;
  path << CurrentProcess::dir() << GetFileBaseName(app_name) << ".atrace.trace";
  return path.str();
}

string AtraceManager::GetFileBaseName(const string &app_name) const {
  std::ostringstream trace_filebase;
  trace_filebase << "atrace-";
  trace_filebase << app_name;
  trace_filebase << "-";
  trace_filebase << clock_->GetCurrentTime();
  return trace_filebase.str();
}

string AtraceManager::GetNextDumpPath() {
  std::ostringstream path;
  path << profiled_app_.trace_path << dumps_created_;
  dumps_created_++;
  return path.str();
}

bool AtraceManager::StopProfiling(const std::string &app_pkg_name,
                                  bool need_result, std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:StopProfiling atrace");
  Log::D("Profiler:Stopping profiling for %s", app_pkg_name.c_str());
  is_profiling_ = false;
  // Should occur after is_profiling is set to false to stop our polling
  // thread.
  dump_data_condition_.notify_all();
  atrace_thread_.join();
  bool isRunning = atrace_->IsAtraceRunning();
  string path = GetNextDumpPath();
  for (int i = 0; i < kRetryStopAttempts && isRunning; i++) {
    // For pre O devices, simply stopping atrace doesn't always write a file.
    // As such we need to create the file first. This allows atrace to
    // properly modify the contents of the file.
    if (DeviceInfo::feature_level() < DeviceInfo::O) {
      DiskFileSystem fs;
      fs.CreateFile(path);
    }
    // Before stopping atrace we write a clock sync marker. We do this because
    // internally to atrace there is a ring buffer of data. The data may
    // clobber the initial clock sync marker.
    atrace_->WriteClockSyncMarker();
    atrace_->Run({profiled_app_.app_pkg_name, path, "--async_stop", ""});
    isRunning = atrace_->IsAtraceRunning();
  }
  if (isRunning) {
    assert(error != nullptr);
    error->append("Failed to stop atrace.");
    return false;
  }
  if (need_result) {
    return CombineFiles(profiled_app_.trace_path.c_str(), dumps_created_,
                        profiled_app_.trace_path.c_str());
  }
  return !isRunning;
}

void AtraceManager::Shutdown() {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:Shutdown atrace");
  if (is_profiling_) {
    Log::D("Profiler:Shutdown atrace");
    is_profiling_ = false;
    atrace_->HardStop();
  }
  // Ensure atrace dump thread exits when shutdown primarily for test.
  if (atrace_thread_.joinable()) {
    dump_data_condition_.notify_all();
    atrace_thread_.join();
  }
}

bool AtraceManager::CombineFiles(const std::string &combine_file_prefix,
                                 int count, const std::string &output_path) {
  std::shared_ptr<File> file = file_system_->GetOrNewFile(output_path);
  file->OpenForWrite();
  if (!file->Exists() || !file->IsOpenForWrite()) {
    return false;
  }

  for (int i = 0; i < count; i++) {
    std::ostringstream file_path;
    file_path << combine_file_prefix << i;
    std::shared_ptr<File> buffer_file = file_system_->GetFile(file_path.str());
    if (!buffer_file->Exists()) {
      return false;
    }
    file_system_->AppendFile(output_path, file_path.str());
    file_system_->DeleteFile(file_path.str());
  }
  file->Close();
  return true;
}

}  // namespace profiler
