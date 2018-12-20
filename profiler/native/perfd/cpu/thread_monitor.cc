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
#include "perfd/cpu/thread_monitor.h"

#include "perfd/cpu/thread_parser.h"

namespace profiler {

using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopResponse;
using profiler::proto::CpuThreadData;
using std::string;
using std::vector;

CpuStartResponse::Status ThreadMonitor::AddProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.insert(pid);
  return CpuStartResponse::SUCCESS;
}

CpuStopResponse::Status ThreadMonitor::RemoveProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.erase(pid);
  return CpuStopResponse::SUCCESS;
}

bool ThreadMonitor::Monitor() {
  vector<int32_t> pids;  // Vector is more efficient than unordered_set.
  {
    // Make a copy of all processes that need a sample. We want to be
    // thread-safe, and we don't want to hold the lock for too long.
    std::lock_guard<std::mutex> lock(pids_mutex_);
    pids = vector<int32_t>(pids_.begin(), pids_.end());
  }
  bool all_succeeded = true;
  for (const int32_t pid : pids) {
    bool process_succeeded = MonitorAProcess(pid);
    if (!process_succeeded) all_succeeded = false;
  }
  return all_succeeded;
}

bool ThreadMonitor::MonitorAProcess(int32_t pid) {
  ThreadsSample sample;
  const auto& found_previous = previous_states_.find(pid);
  ThreadStates new_states;
  bool new_states_collected = CollectStates(pid, &new_states);

  // Timestamp of ThreadsSample message and possibly becoming-dead
  // activities. This timestamp is acquired after calling CollectStates(...)
  // because we want this timestamp to be larger than or equal to any
  // activity's timestamp in this message.
  int64_t timestamp = clock_->GetCurrentTime();

  if (!new_states_collected) {
    // The process is not running.
    if (found_previous == previous_states_.end()) {
      // No previous thread states recorded. Do nothing.
      return true;
    } else {
      // Previous states found. Empty new thread states.
      // Every thread became dead.
      const ThreadStates& old_states = found_previous->second;
      CopyOldStatesToActivities(timestamp, old_states, &sample);
      previous_states_.erase(pid);
      RemoveProcess(pid);
    }
  } else {
    // The process is running. Non-empty new states captured.
    if (found_previous == previous_states_.end()) {
      // No previous thread states. Everything new is an activity.
      CopyNewStatesToActivities(new_states, &sample);
      previous_states_.emplace(pid, new_states);
    } else {
      const ThreadStates& old_states = found_previous->second;
      // Detect the differences. They are activities.
      if (DetectActivities(timestamp, old_states, new_states, &sample)) {
        previous_states_[pid] = new_states;
      }
    }
  }

  // Adds a snapshot of the alive threads to the cache containing their states.
  // They are useful to answer queries regarding the snapshot of thread states
  // at a given moment.
  for (const auto& map : new_states) {
    const ThreadState& state = map.second;
    AddThreadSnapshot(map.first, state.state, state.name, &sample);
  }

  sample.snapshot.set_timestamp(timestamp);
  cache_.AddThreads(pid, sample);
  return true;
}

bool ThreadMonitor::CopyNewStatesToActivities(const ThreadStates& new_states,
                                              ThreadsSample* sample) const {
  for (const auto& map : new_states) {
    AddActivity(map.first, map.second, sample);
  }
  return !new_states.empty();
}

bool ThreadMonitor::CopyOldStatesToActivities(int64_t timestamp,
                                              const ThreadStates& old_states,
                                              ThreadsSample* sample) const {
  bool new_activity_added = false;
  for (const auto& map : old_states) {
    if (map.second.state != CpuThreadData::DEAD) {
      AddActivity(map.first, CpuThreadData::DEAD, map.second.name, timestamp,
                  sample);
      new_activity_added = true;
    }
  }
  return new_activity_added;
}

bool ThreadMonitor::DetectActivities(int64_t timestamp,
                                     const ThreadStates& old_states,
                                     const ThreadStates& new_states,
                                     ThreadsSample* sample) const {
  bool new_activity_added = false;

  // First, check each old thread.
  for (const auto& old : old_states) {
    int32_t tid = old.first;
    const auto& found_new = new_states.find(tid);
    if (found_new != new_states.end()) {
      // Thread is also in the new snapshot.
      if (old.second.state != found_new->second.state) {
        // Thread's state has changed. It is an activity.
        AddActivity(tid, found_new->second, sample);
        new_activity_added = true;
      } else {
        // No thread state change. Do nothing.
      }
    } else {
      // Thread disappeared. It is a DEAD activity.
      // Do not duplicate DEAD activity (if the thread was already known as
      // DEAD).
      if (old.second.state != CpuThreadData::DEAD) {
        AddActivity(tid, CpuThreadData::DEAD, old.second.name, timestamp,
                    sample);
        new_activity_added = true;
      }
    }
  }

  // Detect newly created threads. Each of them is an activity.
  for (const auto& new_state : new_states) {
    int32_t tid = new_state.first;
    const auto& found_old = old_states.find(tid);
    if (found_old == old_states.end()) {
      AddActivity(tid, new_state.second, sample);
      new_activity_added = true;
    }
  }

  return new_activity_added;
}

bool ThreadMonitor::CollectStates(int32_t pid, ThreadStates* states) const {
  vector<int32_t> tids{};  // Thread IDs.
  if (!GetThreads(procfs_.get(), pid, &tids)) return false;

  states->clear();
  for (auto tid : tids) {
    ThreadState state;
    // It is possible a thread is deleted right at this moment.
    if (GetThreadState(procfs_.get(), pid, tid, &state.state, &state.name)) {
      state.timestamp = clock_->GetCurrentTime();
      states->emplace(tid, state);
    }
  }
  // Returns true if we captured at least one thread's state.
  return (states->size() > 0);
}

void ThreadMonitor::AddActivity(int32_t tid, const ThreadState& state,
                                ThreadsSample* sample) const {
  AddActivity(tid, state.state, state.name, state.timestamp, sample);
}

void ThreadMonitor::AddActivity(int32_t tid, CpuThreadData::State state,
                                const string& name, int64_t timestamp,
                                ThreadsSample* sample) const {
  ThreadsSample::Activity activity;
  activity.tid = tid;
  activity.name = name;
  activity.state = state;
  activity.timestamp = timestamp;
  sample->activities.push_back(activity);
}

void ThreadMonitor::AddThreadSnapshot(int32_t tid, CpuThreadData::State state,
                                      const string& name,
                                      ThreadsSample* sample) const {
  auto* snapshot = sample->snapshot.add_threads();
  snapshot->set_tid(tid);
  snapshot->set_state(state);
  snapshot->set_name(name);
}

}  // namespace profiler
