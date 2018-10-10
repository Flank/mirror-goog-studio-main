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

#ifndef PERFD_CPU_ATRACE_MANAGER_H_
#define PERFD_CPU_ATRACE_MANAGER_H_

#include <condition_variable>
#include <map>
#include <mutex>
#include <set>
#include <string>
#include <thread>

#include "utils/clock.h"

namespace profiler {

// Entry storing all data related to an ongoing profiling.
struct AtraceProfilingMetadata {
  // File path where trace will be made available.
  std::string trace_path;
  std::string app_pkg_name;
};

class AtraceManager {
 public:
  explicit AtraceManager(Clock *clock, int dump_data_interval_ms);
  virtual ~AtraceManager() = default;

  // Returns true if profiling of app |app_name| was started successfully.
  // |trace_path| is also set to where the trace file will be made available
  // once profiling of this app is stopped. To call this method on an already
  // profiled app is a noop and returns false.
  // Only one instance of Atrace should be running at a time.
  // TODO: Investigate if running atrace with two different application
  // names keeps the profiling unique.
  bool StartProfiling(const std::string &app_name, int sampling_interval_us,
                      int buffer_size_in_mb, std::string *trace_path,
                      std::string *error);
  bool StopProfiling(const std::string &app_name, bool need_result,
                     std::string *error);
  void Shutdown();
  bool IsProfiling() { return is_profiling_; }
  int GetDumpCount() { return dumps_created_; }

 private:
  Clock *clock_;
  static const char *kAtraceExecutable;
  static const char *kArguments;
  AtraceProfilingMetadata profiled_app_;
  // Protects atrace start/stop
  std::mutex start_stop_mutex_;
  // Used in dump_data_condition.
  std::mutex dump_data_mutex_;
  // Used to block async_dump until timeout, or notifiy is triggered.
  std::condition_variable dump_data_condition_;
  std::thread atrace_thread_;
  std::string categories_;
  std::string buffer_size_arg_;
  int dump_data_interval_ms_;
  int dumps_created_;  // Incremented by the atrace_thread_.
  bool is_profiling_;  // Writen to by main thread, read from by atrace thread.

  // Generate the filename pattern used for trace and log (a name guaranteed
  // not to collide and without an extension).
  std::string GetFileBaseName(const std::string &app_name) const;

  // Generates the trace path to be used for storing trace files.
  virtual std::string GetTracePath(const std::string &app_name) const;

  // Function to dump atrace data periodically this should be run in its own
  // thread.
  void DumpData();

  // Runs atrace with the given arguments, app_name, the path expected for the
  // output, the additional command arguments to pass atrace.
  virtual void RunAtrace(const std::string &app_name, const std::string &path,
                         const std::string &command,
                         const std::string &additonal_args = "");

  // Takes [combine_file_prefix] appends an integer from 0 to count and writes
  // contents to [output_path].
  bool CombineFiles(const std::string &combine_file_prefix, int count,
                    const std::string &output_path);

  // Runs --list_categories on connected device/emulator. Only categories that
  // are supported by the device / emulator are returned. This set of
  // categories is used to restrict what categories are selected when running
  // atrace.
  virtual std::string BuildSupportedCategoriesString();

  // Returns the trace_path with the current count of dumps. Then increments the
  // number of dumps captured.
  std::string GetNextDumpPath();

  // Checks legacy and current system path to see if atrace is running.
  virtual bool IsAtraceRunning();

  // Writes clock sync marker to systrace file before stopping the trace.
  // The marker is written near the end of the trace file.
  // This is done because sometimes the sync marker may be stomped over by
  // the ring buffer internally by atrace. This marker is used to sync
  // the atrace clock with the device boot clock (used by studio).
  virtual void WriteClockSyncMarker();

 protected:
  // Takes the output from atrace --list_categories parses the output and
  // returns the set of supported categories.
  std::set<std::string> ParseListCategoriesOutput(const std::string &output);
};
}  // namespace profiler

#endif  // PERFD_CPU_ATRACE_MANAGER_H_
