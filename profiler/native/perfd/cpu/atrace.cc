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

using profiler::proto::Device;
using std::string;

namespace profiler {

const char *kAtraceExecutable = "/system/bin/atrace";
const char *kTracingFileNames[] = {"/sys/kernel/debug/tracing/tracing_on",
                                   // Legacy tracing file name.
                                   "/sys/kernel/tracing/tracing_on"};

const char *kTracingBufferFileNames[] = {
    "/sys/kernel/debug/tracing/buffer_size_kb",
    // Legacy tracing file name.
    "/sys/kernel/tracing/buffer_size_kb"};
const char *kCategories[] = {"gfx", "input",  "view", "wm",   "am",
                             "sm",  "camera", "hal",  "app",  "res",
                             "pm",  "sched",  "freq", "idle", "load"};
const int kCategoriesCount = sizeof(kCategories) / sizeof(kCategories[0]);

Atrace::Atrace(Clock *clock) : clock_(clock) {
  categories_ = BuildSupportedCategoriesString();
}

void Atrace::Run(const AtraceArgs &run_args) {
  std::ostringstream args;
  args << "-z " << run_args.additional_args << " -a " << run_args.app_pkg_name
       << " -o " << run_args.path << " " << run_args.command << " "
       << categories_;
  profiler::BashCommandRunner atrace(kAtraceExecutable, true);
  atrace.Run(args.str(), nullptr);
}

void Atrace::HardStop() {
  profiler::BashCommandRunner atrace(kAtraceExecutable, true);
  atrace.Run("--async_stop", nullptr);
}

int Atrace::GetBufferSizeKb() {
  return ReadIntFromConfigFile(
      kTracingBufferFileNames,
      sizeof(kTracingBufferFileNames) / sizeof(kTracingBufferFileNames[0]));
}

bool Atrace::IsAtraceRunning() {
  return ReadIntFromConfigFile(
             kTracingFileNames,
             sizeof(kTracingFileNames) / sizeof(kTracingFileNames[0])) == 1;
}

int Atrace::ReadIntFromConfigFile(const char *files[], uint32_t count) {
  DiskFileSystem fs;
  for (uint32_t i = 0; i < count; i++) {
    string contents = fs.GetFileContents(files[i]);
    // Only need to return the value of the first file with a value.
    // The second file is assumed to be for older versions of android.
    if (!contents.empty()) {
      return atoi(contents.c_str());
    }
  }
  return -1;
}

void Atrace::WriteClockSyncMarker() {
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

std::string Atrace::BuildSupportedCategoriesString() {
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

std::set<std::string> Atrace::ParseListCategoriesOutput(
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

}  // namespace profiler
