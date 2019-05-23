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
#include "perfd/cpu/cpu_usage_sampler.h"

#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "perfd/cpu/cpu_cache.h"
#include "proto/common.pb.h"
#include "proto/cpu_data.pb.h"
#include "utils/file_reader.h"
#include "utils/tokenizer.h"

using profiler::FileReader;
using profiler::Tokenizer;
using profiler::proto::CpuCoreUsageData;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopResponse;
using profiler::proto::CpuUsageData;
using std::string;
using std::vector;

namespace {

// Returns how many milliseconds is a time unit used in /proc/* files.
// This function is designed to be called by TimeUnitInMilliseconds() only.
// This function is almost always less efficient than TimeUnitInMilliseconds().
int64_t GetTimeUnitInMilliseconds() {
  int64_t user_hz = sysconf(_SC_CLK_TCK);
  // TODO: Handle other USER_HZ values.
  if (user_hz == 100) {
    return 10;
  } else if (user_hz == 1000) {
    return 1;
  }
  return -1;
}

// Returns how many milliseconds is a time unit used in /proc/* files.
// This function is usually more efficient than GetTimeUnitInMilliseconds().
int64_t TimeUnitInMilliseconds() {
  static const int64_t kTimeUnitInMilliseconds = GetTimeUnitInMilliseconds();
  return kTimeUnitInMilliseconds;
}

// Parses /proc/stat content in |content| and calculates
// |system_cpu_time_in_millisec| and |elapsed_time_in_millisec|. Returns true
// on success.
//
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
//
// |elapsed_time_in_millisec| is the combination of every state, except
// 'guest' (since Linux 2.6.24), as it is included in 'user', and 'guest_nice'
// (since Linux 2.6.33), as it is included by 'guest'.
//
// |system_cpu_time_in_millisec| is the combination of every state of
// |elapsed_time_in_millisec| except 'idle' and 'iowait' (which we also consider
// as idle time).
//
bool ParseProcStatCpuLine(const string& line, int* cpu, int64_t* load,
                          int64_t* elapsed) {
  int64_t user, nice, system, idle, iowait, irq, softirq, steal;
  char cpu_n[11];
  // TODO: figure out why sscanf_s cannot compile.
  if (sscanf(line.c_str(),
             "%10s  %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64
             " %" PRId64 " %" PRId64 " %" PRId64,
             cpu_n, &user, &nice, &system, &idle, &iowait, &irq, &softirq,
             &steal) == 9) {
    *load = user + nice + system + irq + softirq + steal;
    *elapsed = *load + idle + iowait;
    if (strcmp(cpu_n, "cpu") == 0) {
      *cpu = -1;
    } else if (strncmp(cpu_n, "cpu", 3) == 0) {
      *cpu = atoi(cpu_n + 3);
    } else {
      return false;
    }
    return true;
  }
  return false;
}

bool ParseProcStatForUsageData(const string& content, CpuUsageData* data) {
  std::istringstream stream(content);
  std::string line;

  bool found = false;
  while (std::getline(stream, line, '\n')) {
    int64_t load, elapsed;
    int cpu;
    if (ParseProcStatCpuLine(line, &cpu, &load, &elapsed)) {
      if (cpu == -1) {
        data->set_system_cpu_time_in_millisec(load * TimeUnitInMilliseconds());
        data->set_elapsed_time_in_millisec(elapsed * TimeUnitInMilliseconds());
        found = true;
      } else {
        CpuCoreUsageData* core = data->add_cores();
        core->set_core(cpu);
        core->set_system_cpu_time_in_millisec(load * TimeUnitInMilliseconds());
        core->set_elapsed_time_in_millisec(elapsed * TimeUnitInMilliseconds());
      }
    } else {
      break;
    }
  }
  return found;
}

// Collects system-wide data by reading /proc/stat. Returns true on success.
bool CollectSystemUsageData(const string& usage_file, CpuUsageData* data) {
  string buffer;
  if (FileReader::Read(usage_file, &buffer)) {
    return ParseProcStatForUsageData(buffer, data);
  }
  return false;
}

// Parses a process's stat file (proc/[pid]/stat) to collect info. Returns
// true on success.
// The file has only one line, including a number of fields. The fields are
// numbered from 1. A process usage is the sum of the following fields.
//    (14) utime  %lu
//    (15) stime  %lu
//    (16) cutime  %ld
//    (17) cstime  %ld
//
// The following fields are read, although they are not part of usage.
//    (1) pid  %d       -- Used by this function for sanity check.
//    (2) comm  %s      -- Used to map fields to tokens.
//
// The following fields are part of usage, but they are included by utime
// and cutime, respectively. Therefore, they are not read.
//    (43) guest_time  %lu  (since Linux 2.6.24)
//    (44) cguest_time  %ld  (since Linux 2.6.24)
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ParseProcPidStatForUsageData(int32_t pid, const string& content,
                                  CpuUsageData* data) {
  // Find the start and end positions of the second field.
  // The number of words in the file is variable. The second field is the
  // file name of the executable, in parentheses. The file name could include
  // spaces, so if we blindly split the entire line, it would be hard to map
  // words to fields.
  size_t left_parentheses = content.find_first_of('(');
  size_t right_parentheses = content.find_first_of(')');
  if (left_parentheses == string::npos || right_parentheses == string::npos ||
      right_parentheses <= left_parentheses || left_parentheses == 0) {
    return false;
  }

  // Sanity check on pid.
  // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
  int32_t pid_from_file = atoi(content.substr(0, left_parentheses - 1).c_str());
  if (pid_from_file != pid) return false;

  // Each token after the right parenthesis is a field, either a character or a
  // number. The first token is field #3.
  vector<string> tokens =
      Tokenizer::GetTokens(content.substr(right_parentheses + 1), " \n", 11, 4);
  if (tokens.size() == 4) {
    // TODO: Use std::stoll() after we use libc++, and remove '.c_str()'.
    int64_t utime = atol(tokens[0].c_str());
    int64_t stime = atol(tokens[1].c_str());
    int64_t cutime = atol(tokens[2].c_str());
    int64_t cstime = atol(tokens[3].c_str());
    int64_t usage_in_time_units = utime + stime + cutime + cstime;
    data->set_app_cpu_time_in_millisec(usage_in_time_units *
                                       TimeUnitInMilliseconds());
    return true;
  }
  return false;
}

bool CollectProcessUsageData(int32_t pid, const string& usage_file,
                             CpuUsageData* data) {
  string buffer;
  if (FileReader::Read(usage_file, &buffer)) {
    return ParseProcPidStatForUsageData(pid, buffer, data);
  }
  return false;
}

void CollectCpuFrequency(const string& freq_file, CpuCoreUsageData* data) {
  string buffer;
  if (FileReader::Read(freq_file, &buffer)) {
    data->set_frequency_in_khz(atoi(buffer.c_str()));
  }
}

}  // namespace

namespace profiler {

// TODO: Returns a failure if there is no such a running process.
CpuStartResponse::Status CpuUsageSampler::AddProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.insert(pid);
  return CpuStartResponse::SUCCESS;
}

CpuStopResponse::Status CpuUsageSampler::RemoveProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.erase(pid);
  return CpuStopResponse::SUCCESS;
}

bool CpuUsageSampler::Sample() {
  std::unordered_set<int32_t> pids;
  {
    // Make a copy of all processes that need a sample. We want to be
    // thread-safe, and we don't want to hold the lock for too long.
    std::lock_guard<std::mutex> lock(pids_mutex_);
    pids = pids_;
  }
  bool all_succeeded = true;
  for (const int32_t pid : pids) {
    CpuUsageData data;
    bool process_succeeded = SampleAProcess(pid, &data);
    if (process_succeeded) {
      cache_.Add(pid, data);
    } else {
      all_succeeded = false;
    }
  }
  return all_succeeded;
}

// We sample system-wide usage data each time when we sample a process's usage
// data. This is not a waste. It takes non-trial amount of time to sample
// a process's usage data (> 1 millisecond), and therefore it is better to get
// the up-to-date system-wide data each time.
bool CpuUsageSampler::SampleAProcess(int32_t pid, CpuUsageData* data) {
  if (CollectSystemUsageData(usage_files_->GetSystemStatFilePath(), data) &&
      CollectProcessUsageData(pid, usage_files_->GetProcessStatFilePath(pid),
                              data)) {
    for (int i = 0; i < data->cores_size(); i++) {
      // We do not fail if there is no file, just not set it.
      CpuCoreUsageData* data_core = data->mutable_cores(i);
      CollectCpuFrequency(
          usage_files_->GetSystemCurrentCpuFrequencyPath(data_core->core()),
          data_core);
    }
    data->set_end_timestamp(clock_->GetCurrentTime());
    return true;
  }
  return false;
}

}  // namespace profiler
