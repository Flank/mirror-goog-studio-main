/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_
#define PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_

#include "perfd/cpu/profiling_app.h"
#include "proto/commands.pb.h"
#include "proto/common.pb.h"

namespace profiler {

profiler::proto::Event PopulateCpuTraceEvent(
    const ProfilingApp& capture, const profiler::proto::Command& command_data,
    bool is_end);

}  // namespace profiler

#endif  // PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_
