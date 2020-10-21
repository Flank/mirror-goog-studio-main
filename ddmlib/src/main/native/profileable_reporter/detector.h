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
#ifndef PROFILEABLE_REPORTER_DETECTOR_H_
#define PROFILEABLE_REPORTER_DETECTOR_H_

#include <memory>
#include <ostream>
#include <string>
#include <unordered_map>

#include "transport/native/utils/fs/file_system.h"
#include "transport/native/utils/procfs_files.h"

namespace ddmlib {

struct ProcessInfo {
  int32_t pid;
  int64_t start_time;
  std::string package_name;
  bool profileable;

  bool operator==(const ProcessInfo& rhs) const {
    return this->pid == rhs.pid && this->start_time == rhs.start_time &&
           this->package_name == rhs.package_name &&
           this->profileable == rhs.profileable;
  }
};

struct SystemSnapshot {
  // The count of all running processes, being an app or not.
  int all_process_count = 0;
  // Map from a running app's PID to its info. A running app is defined as a
  // process spawned by Zygote.
  std::unordered_map<int32_t, ProcessInfo> apps;

  std::unordered_map<int32_t, ProcessInfo> GetProfileables() const {
    std::unordered_map<int32_t, ProcessInfo> profileables;
    for (const auto& i : apps) {
      if (i.second.profileable) {
        profileables[i.first] = i.second;
      }
    }
    return profileables;
  }
};

class ProfileableChecker {
 public:
  // This class has a derived class for testing.
  virtual ~ProfileableChecker() = default;
  virtual bool Check(int32_t pid, const std::string& package_name) const;
};

// Detector for profileable apps.
class Detector {
 public:
  // The format of the output std::ostream produced by Detector.
  enum class LogFormat {
    kBinary,  // Can be programmatically understood (by ddmlib's host code).
    kHuman,   // Can be easily read by human beings.
    kDebug,   // Similar to kHuman plus info to debug this program itself, e.g.,
              // each profileable app's start time and timing stats.
  };

  Detector(LogFormat log_format, std::unique_ptr<profiler::FileSystem> fs,
           std::unique_ptr<ProfileableChecker> checker)
      : log_format_(log_format),
        fs_(std::move(fs)),
        profileable_checker_(std::move(checker)),
        zygote_pid_(-1),
        zygote64_pid_(-1),
        first_snapshot_done_(false) {}

  Detector(LogFormat log_format);

  Detector(const Detector&) = delete;
  Detector& operator=(const Detector&) = delete;

  // Detects profileable apps and writes the output to stdout.
  // This function is blocking and never returns.
  void Detect();

  // The following methods are marked public for testing.

  // Collects a snapshot of running apps in the system. Prints to the given
  // |stream| the list of profileable apps if they are different from the
  // previous snapshot.
  void Refresh(std::ostream& stream);
  void SetLogFormat(LogFormat format) { log_format_ = format; }
  profiler::FileSystem* file_system() { return fs_.get(); }
  ProfileableChecker* profileable_checker() {
    return profileable_checker_.get();
  }
  const profiler::ProcfsFiles* proc_files() { return &proc_files_; }

 private:
  SystemSnapshot CollectProcessSnapshot();

  // Parses a process's stat file (proc/[pid]/stat) to collect its ppid and
  // start time. The process is of |pid| and the file's content is |content|. If
  // successful, returns true and writes the two pieces of info to |ppid| and
  // |start_time|.
  static bool ParseProcPidStatForPpidAndStartTime(int32_t pid,
                                                  const std::string& content,
                                                  int32_t* ppid,
                                                  int64_t* start_time);

  void PrintProfileables(
      const std::unordered_map<int32_t, ProcessInfo>& profileables,
      std::ostream& output) const;

  bool GetPpidAndStartTime(int32_t pid, int32_t* ppid,
                           int64_t* start_time) const;

  std::string GetPackageName(int32_t pid) const;

  // Returns true if the given pid is zygote64 or zygote by checking its cmdline
  // file.
  bool isZygote64OrZygote(int32_t pid);

  bool isExaminedBefore(int32_t pid, int64_t start_time,
                        const std::string& package_name) const;

  LogFormat log_format_;
  // FileSystem
  std::unique_ptr<profiler::FileSystem> fs_;
  std::unique_ptr<ProfileableChecker> profileable_checker_;
  // Files that are used to obtain process info. Configurable for testing.
  const profiler::ProcfsFiles proc_files_;
  // Pids of zygote processes if known; -1 if not discovered yet.
  int32_t zygote_pid_;
  int32_t zygote64_pid_;
  SystemSnapshot snapshot_;
  bool first_snapshot_done_;  // True if the first snapshot has completed.
};

}  // namespace ddmlib

#endif  // PROFILEABLE_REPORTER_DETECTOR_H_
