/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "cpu_usage_sampler.h"

#include "daemon/event_buffer.h"
#include "perfd/cpu/cpu_usage_sampler.h"
#include "proto/common.pb.h"
#include "proto/cpu.pb.h"

namespace profiler {

using proto::Event;

void CpuUsageDataSampler::Sample() {
  int32_t pid = session().info().pid();
  Event event;
  event.set_pid(pid);
  event.set_group_id(pid);
  event.set_kind(Event::CPU_USAGE);
  auto* usage = event.mutable_cpu_usage();
  usage_sampler_->SampleAProcess(pid, usage);
  buffer()->Add(event);
}
}  // namespace profiler
