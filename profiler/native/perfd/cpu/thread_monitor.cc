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

#include <dirent.h>
#include <cstdlib>  // for atoi()
#include <cstring>  // for strncmp()
#include <sstream>  // for std::ostringstream
#include <vector>

#include "utils/file_reader.h"

using profiler::FileReader;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopResponse;
using profiler::proto::GetThreadsResponse;
using std::string;
using std::vector;

namespace {

// Gets |entries| (not including . and ..) of a given directory |dir|. Returns
// true on success.
// TODO: Refactor this with upcoming utils/ routines.
bool GetDirEntries(const string& dir, vector<string>* entries) {
  DIR* dp;
  struct dirent* dirp;
  if ((dp = opendir(dir.c_str())) == nullptr) return false;
  entries->clear();
  while ((dirp = readdir(dp)) != nullptr) {
    const char* const name = dirp->d_name;
    if (strncmp(name, ".", 1) != 0 && strncmp(name, "..", 2) != 0) {
      entries->push_back(name);
    }
  }
  closedir(dp);
  return true;
}

// Gets thread IDs under a given process of |pid|. Returns true on success.
bool GetThreads(int32_t pid, vector<int32_t>* tids) {
  std::ostringstream os;
  os << "/proc/" << pid << "/task/";
  vector<string> dir_entries;
  if (!GetDirEntries(os.str(), &dir_entries)) return false;
  for (const string& entry : dir_entries) {
    // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
    tids->push_back(atoi(entry.c_str()));
  }
  return true;
}

// Reads /proc/[pid]/task/[tid]/stat file. Returns true on success.
bool ReadThreadStatFile(int32_t pid, int32_t tid, std::string* content) {
  // TODO: Use std::to_string() after we use libc++. NDK doesn't support itoa().
  std::ostringstream os;
  os << "/proc/" << pid << "/task/" << tid << "/stat";
  return FileReader::Read(os.str(), content);
}

// Parses a thread's stat file (proc/[pid]/task/[tid]/stat). If success,
// returns true and saves extracted info into |state| and |name|.
//
// For a thread, the following fields are read (the first field is numbered as
// 1).
//    (1) id  %d                      => For sanity checking.
//    (2) comm  %s (in parentheses)   => Output |name|.
//    (3) state  %c                   => Output |state|.
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ParseThreadStat(int32_t tid, const string& content, char* state,
                     string* name) {
  // Find the start and end positions of the second field.
  // The number of tokens in the file is variable. The second field is the
  // file name of the executable, in parentheses. The file name could include
  // spaces, so if we blindly split the entire line, it would be hard to map
  // words to fields.
  size_t left_parentheses = content.find_first_of('(');
  size_t right_parentheses = content.find_first_of(')');
  if (left_parentheses == string::npos || right_parentheses == string::npos ||
      right_parentheses <= left_parentheses || left_parentheses == 0) {
    return false;
  }

  // Sanity check on tid.
  // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
  int32_t tid_from_file = atoi(content.substr(0, left_parentheses - 1).c_str());
  if (tid_from_file != tid) return false;

  // Between left and right parentheses is the name.
  *name = content.substr(left_parentheses + 1,
                         right_parentheses - left_parentheses - 1);
  // After right parenthese is a space. After the space it is a char standing
  // for state.
  *state = content[right_parentheses + 2];
  return true;
}

// Converts a thread state from character type to an enum type.
// According to http://man7.org/linux/man-pages/man5/proc.5.html, 'W' could mean
// Paging (only before Linux 2.6.0) or Waking (Linux 2.6.33 to 3.13 only).
// Android 1.0 already used kernel 2.6.25.
GetThreadsResponse::State ThreadStateInEnum(char state_in_char) {
  switch (state_in_char) {
    case 'R':
      return GetThreadsResponse::RUNNING;
    case 'S':
      return GetThreadsResponse::SLEEPING;
    case 'D':
      return GetThreadsResponse::WAITING;
    case 'Z':
      return GetThreadsResponse::ZOMBIE;
    case 'T':
      // TODO: Handle the subtle difference before and afer Linux 2.6.33.
      return GetThreadsResponse::STOPPED;
    case 't':
      return GetThreadsResponse::TRACING;
    case 'X':
    case 'x':
      return GetThreadsResponse::DEAD;
    case 'K':
      return GetThreadsResponse::WAKEKILL;
    case 'W':
      return GetThreadsResponse::WAKING;
    case 'P':
      return GetThreadsResponse::PARKED;
    default:
      return GetThreadsResponse::UNSPECIFIED;
  }
}

// Gets |state| and |name| of a given thread of |tid| under process of |pid|.
// Returns true on success.
bool GetThreadState(int32_t pid, int32_t tid, GetThreadsResponse::State* state,
                    string* name) {
  string buffer;
  if (ReadThreadStatFile(pid, tid, &buffer)) {
    char state_in_char;
    if (ParseThreadStat(tid, buffer, &state_in_char, name)) {
      GetThreadsResponse::State enum_state = ThreadStateInEnum(state_in_char);
      if (enum_state != GetThreadsResponse::UNSPECIFIED) {
        *state = enum_state;
        return true;
      }
    }
  }
  return false;
}

}  // namespace

namespace profiler {

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
  int64_t timestamp = clock_.GetCurrentTime();

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
    if (map.second.state != GetThreadsResponse::DEAD) {
      AddActivity(map.first, GetThreadsResponse::DEAD, map.second.name,
                  timestamp, sample);
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
      if (old.second.state != GetThreadsResponse::DEAD) {
        AddActivity(tid, GetThreadsResponse::DEAD, old.second.name, timestamp,
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
  if (!GetThreads(pid, &tids)) return false;

  states->clear();
  for (auto tid : tids) {
    ThreadState state;
    // It is possible a thread is deleted right at this moment.
    if (GetThreadState(pid, tid, &state.state, &state.name)) {
      state.timestamp = clock_.GetCurrentTime();
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

void ThreadMonitor::AddActivity(int32_t tid, GetThreadsResponse::State state,
                                const string& name, int64_t timestamp,
                                ThreadsSample* sample) const {
  ThreadsSample::Activity activity;
  activity.tid = tid;
  activity.name = name;
  activity.state = state;
  activity.timestamp = timestamp;
  sample->activities.push_back(activity);
}

void ThreadMonitor::AddThreadSnapshot(int32_t tid,
                                      GetThreadsResponse::State state,
                                      const string& name,
                                      ThreadsSample* sample) const {
  auto* snapshot = sample->snapshot.add_threads();
  snapshot->set_tid(tid);
  snapshot->set_state(state);
  snapshot->set_name(name);
}

}  // namespace profiler
