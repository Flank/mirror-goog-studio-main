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
#include "cpu_thread_sampler.h"

#include <unordered_set>
#include <vector>

#include "perfd/cpu/thread_parser.h"

namespace profiler {

using proto::CpuThreadData;
using proto::Event;

void CpuThreadSampler::Sample() {
  std::vector<int32_t> tids{};
  if (!GetThreads(procfs_.get(), pid_, &tids)) return;

  // Keep track of existing tids, remove one if seen in the new tids. The rest
  // are dead threads that need to be associated with thread-dead events.
  std::unordered_set<int32_t> potentially_dead_tids;
  for (const auto& item : previous_states_) {
    potentially_dead_tids.insert(item.first);
  }

  for (const auto tid : tids) {
    CpuThreadData::State state;
    std::string name;
    if (GetThreadState(procfs_.get(), pid_, tid, &state, &name)) {
      potentially_dead_tids.erase(tid);
      const auto& search = previous_states_.find(tid);
      if (search == previous_states_.end()) {
        // New thread.
        previous_states_.emplace(tid, state);
      } else if (search->second != state) {
        // New thread state.
        previous_states_[tid] = state;
      } else {
        // Same thread state.
        continue;
      }
      Event event;
      event.set_session_id(session().info().session_id());
      event.set_group_id(tid);
      event.set_kind(Event::CPU_THREAD);
      auto* thread = event.mutable_cpu_thread();
      thread->set_tid(tid);
      thread->set_name(name);
      thread->set_state(state);
      buffer()->Add(event);
    }
  }

  // Add events for dead threads.
  for (const auto tid : potentially_dead_tids) {
    auto& previous_state = previous_states_[tid];
    if (previous_state != CpuThreadData::DEAD) {
      Event event;
      event.set_session_id(session().info().session_id());
      event.set_group_id(tid);
      event.set_kind(Event::CPU_THREAD);
      auto* thread = event.mutable_cpu_thread();
      thread->set_tid(tid);
      // TODO: store thread name.
      thread->set_state(CpuThreadData::DEAD);
      buffer()->Add(event);
    }
    previous_states_.erase(tid);
  }
}
}  // namespace profiler