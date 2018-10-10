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

using profiler::proto::Device;
using std::string;

namespace profiler {

const char *AtraceManager::kAtraceExecutable = "/system/bin/atrace";
const char *kTracingFileNames[] = {"/sys/kernel/debug/tracing/tracing_on",
                                   // Legacy tracing file name.
                                   "/sys/kernel/tracing/tracing_on"};
const int kBufferSize = 1024 * 4;  // About the size of a page.
// Number of times we attempt to run the same atrace command.
const int kRetryAttempts = 5;
const char *kCategories[] = {"gfx", "input",  "view", "wm",   "am",
                             "sm",  "camera", "hal",  "app",  "res",
                             "pm",  "sched",  "freq", "idle", "load"};
const int kCategoriesCount = sizeof(kCategories) / sizeof(kCategories[0]);

AtraceManager::AtraceManager(Clock *clock, int dump_data_interval_ms)
    : clock_(clock),
      dump_data_interval_ms_(dump_data_interval_ms),
      dumps_created_(0),
      is_profiling_(false) {
  categories_ = BuildSupportedCategoriesString();
}

bool AtraceManager::StartProfiling(const std::string &app_pkg_name,
                                   int sampling_interval_us,
                                   int buffer_size_in_mb,
                                   std::string *trace_path,
                                   std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  if (is_profiling_) {
    return false;
  }

  dumps_created_ = 0;
  Trace trace("CPU: StartProfiling atrace");
  Log::D("Profiler:Received query to profile %s", app_pkg_name.c_str());
  std::ostringstream buffer_size_stream;
  buffer_size_stream << "-b " << (buffer_size_in_mb * 1024);
  buffer_size_arg_ = buffer_size_stream.str();
  // Build entry to keep track of what is being profiled.
  profiled_app_.trace_path = GetTracePath(app_pkg_name);
  profiled_app_.app_pkg_name = app_pkg_name;
  // Point trace path to entry's trace path so the trace can be pulled later.
  *trace_path = profiled_app_.trace_path;
  // Check if atrace is already running, if it is its okay to use that instance.
  bool isRunning = IsAtraceRunning();
  for (int i = 0; i < kRetryAttempts && !isRunning; i++) {
    RunAtrace(app_pkg_name, profiled_app_.trace_path, "--async_start",
              buffer_size_arg_);
    isRunning = IsAtraceRunning();
  }
  // This is checked for in the thread below.
  // Setting the value here ensures the thread reads the correct value
  // before executing.
  is_profiling_ = isRunning;
  if (!isRunning) {
    assert(error != nullptr);
    error->append("Failed to run atrace start.");
  } else {
    atrace_thread_ = std::thread(&AtraceManager::DumpData, this);
  }
  return isRunning;
}

void AtraceManager::RunAtrace(const string &app_pkg_name, const string &path,
                              const string &command,
                              const string &additional_arguments) {
  std::ostringstream args;
  args << "-z " << additional_arguments << " -a " << app_pkg_name << " -o "
       << path << " " << command << " " << categories_;
  profiler::BashCommandRunner atrace(kAtraceExecutable);
  // Log when we run an atrace command, this will help in the future if we have
  // any errors.
  Log::D("Running Atrace with the following args: %s", args.str().c_str());
  atrace.Run(args.str(), nullptr);
}

bool AtraceManager::IsAtraceRunning() {
  DiskFileSystem fs;
  int fileNameCount = sizeof(kTracingFileNames) / sizeof(kTracingFileNames[0]);
  bool isRunning = false;
  for (int i = 0; i < fileNameCount; i++) {
    string contents = fs.GetFileContents(kTracingFileNames[i]);
    // Only need to test the value of the first file with a value.
    if (!contents.empty()) {
      isRunning = contents[0] == '1';
      break;
    }
  }
  return isRunning;
}

std::string AtraceManager::BuildSupportedCategoriesString() {
  string output;
  profiler::BashCommandRunner atrace(kAtraceExecutable);
  atrace.Run("--list_categories", &output);
  std::set<std::string> supportedCategories = ParseListCategoriesOutput(output);
  std::ostringstream categories;
  for (int i = 0; i < kCategoriesCount; i++) {
    if (supportedCategories.find(string(kCategories[i])) !=
        supportedCategories.end()) {
      categories << " " << kCategories[i];
    }
  }
  return categories.str();
}

std::set<std::string> AtraceManager::ParseListCategoriesOutput(
    const std::string &output) {
  string category;
  std::set<std::string> supportedCategories;
  std::istringstream categoriesList(output);
  while (getline(categoriesList, category, '\n')) {
    std::string name;
    Tokenizer tokenizer = Tokenizer{category, " - "};
    if (tokenizer.GetNextToken(&name)) {
      supportedCategories.emplace(name);
    }
  }
  return supportedCategories;
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
      RunAtrace(profiled_app_.app_pkg_name, GetNextDumpPath(), "--async_dump",
                buffer_size_arg_);
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
  // Should occur after is_profiling is set to false to stop our polling thread.
  dump_data_condition_.notify_all();
  atrace_thread_.join();
  bool isRunning = IsAtraceRunning();
  string path = GetNextDumpPath();
  for (int i = 0; i < kRetryAttempts && isRunning; i++) {
    // For pre O devices, simply stopping atrace doesn't always write a file.
    // As such we need to create the file first. This allows atrace to properly
    // modify the contents of the file.
    if (DeviceInfo::feature_level() < Device::O) {
      DiskFileSystem fs;
      fs.CreateFile(path);
    }
    // Before stopping atrace we write a clock sync marker. We do this because
    // internally to atrace there is a ring buffer of data. The data may clobber
    // the initial clock sync marker.
    WriteClockSyncMarker();
    RunAtrace(profiled_app_.app_pkg_name, path, "--async_stop");
    isRunning = IsAtraceRunning();
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

void AtraceManager::WriteClockSyncMarker() {
  const std::string debugfs_path = "/sys/kernel/debug/tracing/";
  const std::string tracefs_path = "/sys/kernel/tracing/";
  const std::string trace_file = "trace_marker";
  std::string write_path = "";
  bool tracefs = access((tracefs_path + trace_file).c_str(), F_OK) != -1;
  bool debugfs = access((debugfs_path + trace_file).c_str(), F_OK) != -1;

  if (!tracefs && !debugfs) {
    Log::E("Atrace: Did not find trace folder");
    return;
  }

  if (tracefs) {
    write_path.append(tracefs_path);
  } else {
    write_path.append(debugfs_path);
  }
  write_path.append(trace_file);

  char buffer[128];
  int len = 0;
  int fd = open((write_path).c_str(), O_WRONLY);
  if (fd == -1) {
    Log::E("Atrace: error opening %s: %s (%d)", write_path.c_str(),
           strerror(errno), errno);
    return;
  }
  float now_in_seconds = clock_->GetCurrentTime() / 1000000000.0f;

  // Write the clock sync marker in the same format as the initial
  len = snprintf(buffer, 128, "trace_event_clock_sync: parent_ts=%f\n",
                 now_in_seconds);
  if (write(fd, buffer, len) != len) {
    Log::E("Atrace: error writing clock sync marker %s (%d)", strerror(errno),
           errno);
  }
  close(fd);
}

void AtraceManager::Shutdown() {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:Shutdown atrace");
  if (is_profiling_) {
    Log::D("Profiler:Shutdown atrace");
    is_profiling_ = false;
    profiler::BashCommandRunner atrace(kAtraceExecutable);
    atrace.Run("--async_stop", nullptr);
  }
}

bool AtraceManager::CombineFiles(const std::string &combine_file_prefix,
                                 int count, const std::string &output_path) {
  FILE *file = fopen(output_path.c_str(), "wb");
  if (file == nullptr) {
    return false;
  }

  char buffer[kBufferSize];
  for (int i = 0; i < count; i++) {
    off_t offset = 0;
    int read_size = 0;
    std::ostringstream filePath;
    filePath << combine_file_prefix << i;
    int dump_file = open(filePath.str().c_str(), O_RDONLY);
    while ((read_size = pread(dump_file, buffer, kBufferSize, offset)) > 0) {
      offset += read_size;
      fwrite(buffer, sizeof(char), read_size, file);
    }
    close(dump_file);
    remove(filePath.str().c_str());
  }
  fclose(file);
  return true;
}

}  // namespace profiler
