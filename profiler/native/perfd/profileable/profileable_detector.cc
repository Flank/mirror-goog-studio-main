/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include "perfd/profileable/profileable_detector.h"

#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#include "utils/bash_command.h"
#include "utils/clock.h"
#include "utils/file_reader.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/nonblocking_command_runner.h"
#include "utils/procfs_files.h"
#include "utils/thread_name.h"
#include "utils/tokenizer.h"
#include "utils/trace.h"

using profiler::BashCommandRunner;
using profiler::DiskFileSystem;
using profiler::FileSystem;
using profiler::Log;
using profiler::NonBlockingCommandRunner;
using profiler::PathStat;
using profiler::Tokenizer;
using profiler::Trace;
using profiler::proto::Event;
using profiler::proto::Process;
using std::set;
using std::string;
using std::unordered_map;
using std::vector;

namespace profiler {

namespace {
const int kProfileStopTryTimesLimit = 6;

template <typename K, typename V>
set<K> GetKeys(const unordered_map<K, V> map) {
  set<int32_t> keys;
  std::for_each(map.begin(), map.end(),
                [&keys](const std::pair<K, V>& p) { keys.insert(p.first); });
  return keys;
}

}  // namespace

// Attempt to run ART method sampling as a way to check if the given process
// is profileable.
bool ProfileableChecker::Check(int32_t pid, const string& package_name) const {
  BashCommandRunner tester{"/system/bin/cmd", true};
  std::ostringstream oss;
  // Start method sampling at the sample interval of 1 second. We don't need
  // the data; we just check if the command would succeed. (The command line
  // argument is in microseconds. The maximum acceptable value is
  // 2,147,483,647. However, a very long interval such as 30 minutes may
  // add overhead to the process and system which leads to ANR.)
  oss << "activity profile start --sampling 1000000 " << package_name
      << " /data/local/tmp/profileable_reporter.tmp 2>/dev/null";
  bool start_succeeded = tester.Run(oss.str(), nullptr);
  if (!start_succeeded) return false;

  // BashCommandRunner::Run() returns false if it cannot invoke the command. If
  // so, try up to 5 times as the best effort. If still unsuccessful, log the
  // failure as the app would remain in the method sampling mode which will make
  // ART ignore the next method tracing/sampling start request.
  //
  // Choose BashCommandRunner over NonBlockingCommandRunner because the timing
  // of the former's execution is more predictable. We want to stop the sampling
  // quickly to minimize the interference with the app's startup performance.
  // The promptness of process discovery is relatively secondary.
  int try_count = 0;
  bool stop_succeeded = true;
  do {
    BashCommandRunner stopper{"/system/bin/cmd", true};
    oss.str("");
    oss << "activity profile stop " << package_name;
    stop_succeeded = stopper.Run(oss.str(), nullptr);
    try_count++;
  } while (!stop_succeeded && try_count < kProfileStopTryTimesLimit);
  if (!stop_succeeded) {
    Log::W(Log::Tag::PROFILER, "Failed to stop method sampling for %s",
           package_name.c_str());
  }
  // The app is profileable regardless of whether the stop succeeded.
  return true;
}

ProfileableDetector::ProfileableDetector(Clock* clock, EventBuffer* buffer)
    : ProfileableDetector(
          clock, buffer, std::unique_ptr<FileSystem>(new DiskFileSystem()),
          std::unique_ptr<ProfileableChecker>(new ProfileableChecker())) {}

ProfileableDetector::~ProfileableDetector() {
  if (running_.exchange(false)) {
    if (detector_thread_.joinable()) {
      detector_thread_.join();
    }
  }
}

void ProfileableDetector::Start() {
  Log::V(Log::Tag::PROFILER, "Start detecting profileable processes");
  running_ = true;
  detector_thread_ = std::thread([this]() {
    SetThreadName("DetectPable");
    while (running_.load()) {
      Refresh();
      // Always sleep regardless of how long the last refresh takes, so it's
      // not taking a whole CPU core on a lower end device.
      std::this_thread::sleep_for(std::chrono::milliseconds(1000));
    };
  });
}

void ProfileableDetector::Refresh() {
  SystemSnapshot current = CollectProcessSnapshot();
  const auto& previous_profileables = snapshot_.GetProfileables();
  const auto& current_profileables = current.GetProfileables();
  // Print snapshot on first request or when the profileable apps change.
  if (!first_snapshot_done_ || previous_profileables != current_profileables) {
    DetectChanges(previous_profileables, current_profileables);
  }
  first_snapshot_done_ = true;
  snapshot_ = current;
}

SystemSnapshot ProfileableDetector::CollectProcessSnapshot() {
  Trace trace("ProfileableDetector::CollectProcessSnapshot");
  SystemSnapshot result;

  // List /proc/ and retrieve app process info.
  fs_->WalkDir("/proc",
               [this, &result](const PathStat& path_stat) {
                 if (path_stat.type() != PathStat::Type::DIR) return;

                 int pid = atoi(path_stat.rel_path().c_str());
                 if (pid == 0) return;

                 result.all_process_count++;

                 int32_t ppid = 0;
                 int64_t start_time = 0;
                 if (!GetPpidAndStartTime(pid, &ppid, &start_time)) {
                   // The /proc/PID/stat file is unavailable or invalid.
                   return;
                 }
                 if (!isZygote64OrZygote(ppid)) {
                   // The process is not an app.
                   return;
                 }

                 string package_name = GetPackageName(pid);
                 if (package_name.empty()) {
                   // The process hasn't updated /proc/PID/cmdline by its app
                   // name yet, or the process has ended.
                   return;
                 }

                 bool profileable = false;
                 if (isExaminedBefore(pid, start_time, package_name)) {
                   profileable = snapshot_.apps[pid].profileable;
                 } else {
                   profileable = profileable_checker_->Check(pid, package_name);
                 }

                 ProcessInfo process;
                 process.pid = pid;
                 process.start_time = start_time;
                 process.package_name = package_name;
                 process.profileable = profileable;
                 result.apps[pid] = process;
               },
               1);
  return result;
}

// Parses a process's stat file (proc/[pid]/stat) to collect info. Returns
// true on success.
// The file has only one line, including a number of fields. The fields are
// numbered from 1. The start time is field 22.
//    (4)  ppid  %d
//         The PID of the parent of this process.
//    (22) starttime  %llu
//         Since Linux 2.6, the value is expressed in clock ticks (divide
//         by sysconf(_SC_CLK_TCK)).
//
// The following fields are read, although they are not part of usage.
//    (1) pid  %d       -- Used by this function for validity check.
//    (2) comm  %s      -- Used to map fields to tokens.
//
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ProfileableDetector::ParseProcPidStatForPpidAndStartTime(
    int32_t pid, const string& content, int32_t* ppid, int64_t* start_time) {
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

  // Validity check on pid.
  // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
  int32_t pid_from_file = atoi(content.substr(0, left_parentheses - 1).c_str());
  if (pid_from_file != pid) return false;

  // Each token after the right parenthesis is a field, either a character or
  // a number. The first token is field #3.
  vector<string> tokens =
      Tokenizer::GetTokens(content.substr(right_parentheses + 1), " \n", 1, 19);
  if (tokens.size() == 19) {
    // TODO: Use std::stoll() after we use libc++, and remove '.c_str()'.
    *ppid = atoi(tokens[0].c_str());
    *start_time = atol(tokens[18].c_str());
    return true;
  }
  return false;
}

void ProfileableDetector::DetectChanges(
    const unordered_map<int32_t, ProcessInfo>& previous,
    const unordered_map<int32_t, ProcessInfo>& current) {
  set<int32_t> previous_pids = GetKeys(previous);
  set<int32_t> current_pids = GetKeys(current);
  set<int32_t> added_pids;
  std::set_difference(current_pids.begin(), current_pids.end(),
                      previous_pids.begin(), previous_pids.end(),
                      std::inserter(added_pids, added_pids.begin()));
  set<int32_t> deleted_pids;
  std::set_difference(previous_pids.begin(), previous_pids.end(),
                      current_pids.begin(), current_pids.end(),
                      std::inserter(deleted_pids, deleted_pids.begin()));
  for (int32_t pid : deleted_pids) {
    GenerateProcessEvent(previous.at(pid), true);
  }
  for (int32_t pid : added_pids) {
    GenerateProcessEvent(current.at(pid), false);
  }
}

void ProfileableDetector::GenerateProcessEvent(const ProcessInfo& process,
                                               bool is_ended) {
  assert(process.profileable);

  Event event;
  event.set_pid(process.pid);
  event.set_group_id(process.pid);
  event.set_kind(Event::PROCESS);
  event.set_is_ended(is_ended);

  if (!is_ended) {
    auto* data =
        event.mutable_process()->mutable_process_started()->mutable_process();
    data->set_name(process.package_name);
    data->set_pid(process.pid);
    // No need to set |device_id|. Host knowns which stream an event comes from.
    data->set_state(proto::Process::ALIVE);
    data->set_start_timestamp_ns(clock_->GetCurrentTime());
    // No need to set abi_cpu_arch for profileable processes.
    data->set_exposure_level(Process::PROFILEABLE);
  }
  buffer_->Add(event);
}

bool ProfileableDetector::GetPpidAndStartTime(int32_t pid, int32_t* ppid,
                                              int64_t* start_time) const {
  string stat_path = proc_files_.GetProcessStatFilePath(pid);
  string content = fs_->GetFileContents(stat_path);
  if (content.empty()) return false;
  return ParseProcPidStatForPpidAndStartTime(pid, content, ppid, start_time);
}

string ProfileableDetector::GetPackageName(int32_t pid) const {
  string cmdline_path = proc_files_.GetProcessCmdlineFilePath(pid);
  string cmdline = fs_->GetFileContents(cmdline_path);
  // cmdline contains a sequence of null terminated string. We want
  // to keep only the first one to extract the binary name.
  return string(cmdline, 0, strlen(cmdline.c_str()));
}

// Returns true if the given pid's cmdline is zygote64 or zygote.
bool ProfileableDetector::isZygote64OrZygote(int32_t pid) {
  if (zygote64_pid_ != -1 && zygote64_pid_ == pid) return true;
  if (zygote_pid_ != -1 && zygote_pid_ == pid) return true;
  string name = GetPackageName(pid);
  if (name == "zygote64") {
    zygote64_pid_ = pid;
    return true;
  } else if (name == "zygote") {
    zygote_pid_ = pid;
    return true;
  }
  return false;
}

bool ProfileableDetector::isExaminedBefore(int32_t pid, int64_t start_time,
                                           const string& package_name) const {
  auto found = snapshot_.apps.find(pid);
  if (found == snapshot_.apps.end()) return false;
  return found->second.start_time == start_time &&
         found->second.package_name == package_name;
}

}  // namespace profiler
