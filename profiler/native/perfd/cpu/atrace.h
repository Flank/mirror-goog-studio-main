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

#ifndef PERFD_CPU_ATRACE_H_
#define PERFD_CPU_ATRACE_H_

#include <set>
#include <string>

#include "utils/clock.h"

namespace profiler {

// Arguments for running the atrace command.
struct AtraceArgs {
  std::string app_pkg_name;
  std::string path;
  std::string command;
  std::string additional_args;
};

class Atrace {
 public:
  explicit Atrace(Clock* clock);
  virtual ~Atrace() = default;

  // Runs atrace with the given arguments, app_name, the path expected for the
  // output, the additional command arguments to pass atrace.
  virtual void Run(const AtraceArgs& run_args);

  // Checks legacy and current system path to see if atrace is running.
  virtual bool IsAtraceRunning();

  // Writes clock sync marker to systrace file before stopping the trace.
  // The marker is written near the end of the trace file.
  // This is done because sometimes the sync marker may be stomped over by
  // the ring buffer internally by atrace. This marker is used to sync
  // the atrace clock with the device boot clock (used by studio).
  virtual void WriteClockSyncMarker();

  // Reads the Atrace buffer size (in KB) from the tracer pipe. -1 is returned
  // if no buffer can be read. Note: A valid buffer size can be returned if
  // Atrace is running or not.
  virtual int GetBufferSizeKb();

  // Stops atrace without capturing output. This function should be called
  // only in abnormal circumstances.
  virtual void HardStop();

 private:
  Clock* clock_;
  std::string categories_;

  // Runs --list_categories on connected device/emulator. Only categories that
  // are supported by the device / emulator are returned. This set of
  // categories is used to restrict what categories are selected when running
  // atrace.
  virtual std::string BuildSupportedCategoriesString();

  // Helper function to read int values from Atrace files. This function will
  // enumerate all |files| returning the first int value read from a file. The
  // array is expected to contain the paths to the config files for current and
  // past versions of android.
  int ReadIntFromConfigFile(const char* files[], uint32_t count);

 protected:
  // Takes the output from atrace --list_categories parses the output and
  // returns the set of supported categories.
  std::set<std::string> ParseListCategoriesOutput(const std::string& output);
};
}  // namespace profiler

#endif  // PERFD_CPU_ATRACE_MANAGER_H_
