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
#ifndef PERFD_CPU_THREAD_MONITOR_H_
#define PERFD_CPU_THREAD_MONITOR_H_

#include <cstdint>
#include <string>
#include <unordered_map>
#include <unordered_set>

#include "perfd/cpu/cpu_cache.h"
#include "proto/cpu.grpc.pb.h"
#include "proto/cpu.pb.h"
#include "utils/clock.h"

namespace profiler {

// Monitors thread activities for a set of processes. A thread activity is
// defined as a change of thread state. When a thread is created, its state is
// observed as changing from null to something such as running. It is considered
// an activity of 'running'. When a thread is deleted, its state is observed as
// changing from something such as running to null. It is considered an activity
// of 'dead'.
class ThreadMonitor {
 public:
  // Creates a thread monitor that detects and saves activities to |cpu_cache|.
  ThreadMonitor(Clock* clock, CpuCache* cpu_cache)
      : clock_(clock), cache_(*cpu_cache) {}

  // Starts collecting thread activity for process with ID of |pid|. Does
  // nothing if the process has been monitored.
  profiler::proto::CpuStartResponse::Status AddProcess(int32_t pid);

  // Stops collecting thread activity for process with ID of |pid|. Does
  // nothing if |pid| is not being monitored.
  profiler::proto::CpuStopResponse::Status RemoveProcess(int32_t pid);

  // Monitors all processes that need monitoring. Detects thread activities and
  // saves them to |cache_|. Returns true if successfully monitored all
  // processes (no errors encountered).
  bool Monitor();

 private:
  // State of a thread at a given point.
  struct ThreadState {
    int64_t timestamp;
    std::string name;
    profiler::proto::GetThreadsResponse::State state;
  };

  // States of all threads in a given process.
  // Map from a thread ID to its state.
  using ThreadStates = std::unordered_map<int32_t, ThreadState>;

  // Thread states of a number of processes.
  // Map from a process ID to its thread states.
  using States = std::unordered_map<int32_t, ThreadStates>;

  // Monitors thread activities for the process of |pid|. Saves activities into
  // |cache_|. Returns true on success (no errors encountered).
  // If there is no running process of |pid|, still returns true and stops
  // monitoring it.
  bool MonitorAProcess(int32_t pid);
  // Adds activities into |sample|, considering all thread states
  // in |new_states| as activities.
  // Returns true if at least one activity is added.
  // This method is expected to be called when a process is observed for the
  // first time.
  bool CopyNewStatesToActivities(const ThreadStates& new_states,
                                 ThreadsSample* sample) const;
  // Adds becoming-dead activities into |sample|, considering all
  // threads in |old_states| became dead at |timestamp|.
  // Returns true if at least one activity is added.
  // This method is expected to be called when a process is deleted.
  // Does not add an activity if the thread is last known as dead because a
  // becoming-dead activity should already be recorded.
  bool CopyOldStatesToActivities(int64_t timestamp,
                                 const ThreadStates& old_states,
                                 ThreadsSample* sample) const;
  // Adds activities into |sample|, considering all differences
  // between |old_states| and |new_states| as activities.
  // Returns true if at least one activity is added.
  // If a thread disappeared, adds an activity that it became dead at
  // |sample| timestamp. However, doesn't add the activity if the thread is last
  // known as dead because a becoming-dead activity should already be recorded.
  bool DetectActivities(int64_t timestamp, const ThreadStates& old_states,
                        const ThreadStates& new_states,
                        ThreadsSample* sample) const;
  // Collects thread states of a given process of |pid|. Returns true if at
  // least one thread's state is captured.
  bool CollectStates(int32_t pid, ThreadStates* states) const;
  // Adds an activity of thread |tid| into |sample|, considering |state| is the
  // activity.
  void AddActivity(int32_t tid, const ThreadState& state,
                   ThreadsSample* sample) const;
  // Adds an activity of thread |tid| into |sample| with given information.
  void AddActivity(int32_t tid,
                   profiler::proto::GetThreadsResponse::State state,
                   const std::string& name, int64_t timestamp,
                   ThreadsSample* sample) const;
  // Adds the state of a thread |tid| into |sample|.
  void AddThreadSnapshot(int32_t tid,
                         profiler::proto::GetThreadsResponse::State state,
                         const std::string& name, ThreadsSample* sample) const;

  // PIDs of app process that are being monitored.
  std::unordered_set<int32_t> pids_{};
  std::mutex pids_mutex_;
  // Clock that timestamps thread activities.
  Clock* clock_;
  // Cache where collected data will be saved.
  CpuCache& cache_;
  // Last known thread states of all process being monitored.
  States previous_states_{};
};

}  // namespace profiler

#endif  // PERFD_CPU_THREAD_MONITOR_H_
