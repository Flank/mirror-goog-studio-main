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
#ifndef PERFD_CPU_PROCFS_FILES_H_
#define PERFD_CPU_PROCFS_FILES_H_

#include <string>

namespace profiler {

// A class that provide paths of files that are usually found in /proc file
// system for CPU profiling. Designed to be mockable for easy testing.
class ProcfsFiles {
 public:
  virtual ~ProcfsFiles() = default;

  virtual std::string GetSystemStatFilePath() const;
  virtual std::string GetProcessStatFilePath(int32_t pid) const;
  virtual std::string GetProcessTaskDir(int32_t pid) const;
  virtual std::string GetSystemCpuPath() const;
  virtual std::string GetSystemCurrentCpuFrequencyPath(int32_t cpu) const;
  virtual std::string GetSystemMinCpuFrequencyPath(int32_t cpu) const;
  virtual std::string GetSystemMaxCpuFrequencyPath(int32_t cpu) const;
  virtual std::string GetThreadStatFilePath(int32_t pid, int32_t tid) const;

  // Maps file usually is accessable only by the app itself or the root.
  virtual std::string GetMemoryMapFilePath(int32_t pid) const;
};

}  // namespace profiler

#endif  // PERFD_CPU_PROCFS_FILES_H_
