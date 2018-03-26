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
#include "perfd/cpu/cpu_config.h"

#include <stdio.h>
#include <string>

#include "proto/common.pb.h"
#include "utils/file_reader.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/procfs_files.h"

using grpc::Status;
using grpc::StatusCode;
using profiler::FileReader;
using profiler::Log;
using profiler::PathStat;
using profiler::ProcfsFiles;
using profiler::proto::CpuCoreConfigResponse;
using std::string;

namespace {

bool GetCpuConfig(const string& freq_file, int32_t* frequency_in_khz) {
  string buffer;
  if (FileReader::Read(freq_file, &buffer)) {
    *frequency_in_khz = atoi(buffer.c_str());
    return true;
  }
  Log::D("Could not open CPU config file: %s\n", freq_file.c_str());
  return false;
}

bool ParseFrequencyFiles(const ProcfsFiles& proc_fs,
                         CpuCoreConfigResponse* response,
                         const PathStat& pstat) {
  const string& rel_path = pstat.rel_path();
  if (rel_path.compare("cpu") == 0) {
    return true;
  }
  if (rel_path.compare(0, 3, "cpu") == 0) {
    if (pstat.type() != PathStat::Type::DIR || !isdigit(rel_path.at(3))) {
      return true;
    }
    // This is the valid case if it "reaches" here.
  } else {
    return true;
  }

  int32_t core_number = atoi(rel_path.substr(3).c_str());
  int32_t min_frequency_in_khz, max_frequency_in_khz;

  CpuCoreConfigResponse::CpuCoreConfigData* core_config =
      response->add_configs();
  core_config->set_core(core_number);
  if (GetCpuConfig(proc_fs.GetSystemMinCpuFrequencyPath(core_number),
                   &min_frequency_in_khz)) {
    core_config->set_min_frequency_in_khz(min_frequency_in_khz);
  } else {
    return false;
  }

  if (GetCpuConfig(proc_fs.GetSystemMaxCpuFrequencyPath(core_number),
                   &max_frequency_in_khz)) {
    core_config->set_max_frequency_in_khz(max_frequency_in_khz);
  } else {
    return false;
  }

  return true;
}

}  // namespace

namespace profiler {

grpc::Status CpuConfig::GetCpuCoreConfig(CpuCoreConfigResponse* response) {
  ProcfsFiles proc_fs;
  return GetCpuCoreConfig(proc_fs, response);
}

grpc::Status CpuConfig::GetCpuCoreConfig(const ProcfsFiles& proc_fs,
                                         CpuCoreConfigResponse* response) {
  DiskFileSystem fs;
  // If the system CPU path is relative, then append it to the working dir.
  // This solves the issue of running the same code in Bazel vs on a device.
  auto cpu_sys_dir = fs.GetDir(
      Path::AppendIfRelative(fs.GetWorkingDir(), proc_fs.GetSystemCpuPath()));
  if (!cpu_sys_dir->Exists()) {
    return grpc::Status(StatusCode::FAILED_PRECONDITION,
                        "Could not locate cpu system dir.");
  }

  bool keep_walking = true;
  fs.WalkDir(cpu_sys_dir->path(),
             [response, &proc_fs, &keep_walking](const PathStat& pstat) {
               if (!keep_walking) {
                 return;
               }
               keep_walking &= ParseFrequencyFiles(proc_fs, response, pstat);
             },
             1);

  if (!keep_walking) {
    return grpc::Status(StatusCode::FAILED_PRECONDITION,
                        "Error parsing frequency files.");
  }
  return Status::OK;
}

}  // namespace profiler
