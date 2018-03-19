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

#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"
#include "utils/tokenizer.h"

using std::string;

namespace profiler {

const char *AtraceManager::kAtraceExecutable = "/system/bin/atrace";
const int kBufferSize = 1024 * 4;  // About the size of a page.
const char *kCategories[] = {"gfx", "input",  "view", "wm",   "am",
                             "sm",  "camera", "hal",  "app",  "res",
                             "pm",  "sched",  "freq", "idle", "load"};
const int kCategoriesCount = sizeof(kCategories) / sizeof(kCategories[0]);

AtraceManager::AtraceManager(Clock* clock, int dump_data_interval_ms)
    : clock_(clock),
      dump_data_interval_ms_(dump_data_interval_ms),
      dumps_created_(0),
      is_profiling_(false) {
  categories_ = BuildSupportedCategoriesString();
}

AtraceManager::~AtraceManager() {}

bool AtraceManager::StartProfiling(const std::string &app_pkg_name,
                                   int sampling_interval_us,
                                   std::string *trace_path,
                                   std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  if (is_profiling_) {
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

  RunAtrace(app_pkg_name, profiled_app_.trace_path, "--async_start");
  is_profiling_ = true;
  atrace_thread_ = std::thread(&AtraceManager::DumpData, this);
  return true;
}

void AtraceManager::RunAtrace(const string &app_pkg_name, const string &path,
                              const string &command) {
  std::ostringstream args;
  args << "-z -b 4096"
       << " -a " << app_pkg_name << " -o " << path << " " << command << " "
       << categories_;
  profiler::BashCommandRunner atrace(kAtraceExecutable);
  atrace.Run(args.str(), nullptr);
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
    RunAtrace(profiled_app_.app_pkg_name, GetNextDumpPath(), "--async_dump");
    std::this_thread::sleep_for(
        std::chrono::milliseconds(dump_data_interval_ms_));
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

bool AtraceManager::StopProfiling(const std::string &app_pkg_name, bool need_result,
                                  std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:StopProfiling atrace");
  Log::D("Profiler:Stopping profiling for %s", app_pkg_name.c_str());
  is_profiling_ = false;
  atrace_thread_.join();
  RunAtrace(profiled_app_.app_pkg_name, GetNextDumpPath(), "--async_stop");
  if (need_result) {
    return CombineFiles(profiled_app_.trace_path.c_str(), dumps_created_,
                        profiled_app_.trace_path.c_str());
  } else {
    return true;
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
