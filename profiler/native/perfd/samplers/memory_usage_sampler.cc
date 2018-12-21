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
#include "memory_usage_sampler.h"

#include "proto/common.pb.h"

namespace profiler {

using proto::Event;

void MemoryUsageSampler::Sample() {
  Event event;
  event.set_pid(pid_);
  event.set_kind(Event::MEMORY_USAGE);
  auto* usage = event.mutable_memory_usage();
  reader_->GetProcessMemoryLevels(pid_, usage);
  buffer()->Add(event);
}
}  // namespace profiler
