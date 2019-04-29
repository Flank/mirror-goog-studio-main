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
#ifndef PERFD_CPU_CPU_CONFIG_H_
#define PERFD_CPU_CPU_CONFIG_H_

#include <grpc++/grpc++.h>

#include "proto/cpu_data.pb.h"
#include "utils/procfs_files.h"

namespace profiler {

class CpuConfig {
 public:
  static grpc::Status GetCpuCoreConfig(proto::CpuCoreConfigData* data);

  static grpc::Status GetCpuCoreConfig(const ProcfsFiles& proc_fs,
                                       proto::CpuCoreConfigData* data);
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_CONFIG_H_
