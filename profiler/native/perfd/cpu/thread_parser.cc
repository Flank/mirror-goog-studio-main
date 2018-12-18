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
#include "thread_parser.h"

#include <cstdlib>   // for atoi()
#include <string>
#include <vector>

#include "proto/cpu_data.pb.h"
#include "utils/file_reader.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/procfs_files.h"

namespace profiler {

using profiler::FileReader;
using profiler::Log;
using profiler::proto::CpuThreadData;
using std::string;
using std::vector;

bool GetThreads(const ProcfsFiles* procfs, int32_t pid, vector<int32_t>* tids) {
  DiskFileSystem fs;
  auto dir = fs.GetDir(procfs->GetProcessTaskDir(pid));
  if (!dir->Exists()) {
    Log::E("Directory %s doesn't exist.", dir->path().c_str());
    return false;
  }
  // List thread ID directories under /proc/[pid]/task/ directory.
  dir->Walk(
      [tids](const PathStat& pstat) {
        if (pstat.type() == PathStat::Type::DIR) {
          // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
          tids->push_back(atoi(pstat.rel_path().c_str()));
        }
      },
      1);
  return true;
}

bool GetThreadState(const ProcfsFiles* procfs, int32_t pid, int32_t tid,
                    CpuThreadData::State* state, string* name) {
  string buffer;
  // Reads /proc/[pid]/task/[tid]/stat file.
  const string& thread_stat_file = procfs->GetThreadStatFilePath(pid, tid);
  if (FileReader::Read(thread_stat_file, &buffer)) {
    char state_in_char;
    if (ParseThreadStat(tid, buffer, &state_in_char, name)) {
      CpuThreadData::State enum_state = ThreadStateInEnum(state_in_char);
      if (enum_state != CpuThreadData::UNSPECIFIED) {
        *state = enum_state;
        return true;
      }
    }
  }
  Log::E("Failed to parse stat file %s.", thread_stat_file.c_str());
  return false;
}

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

CpuThreadData::State ThreadStateInEnum(char state_in_char) {
  switch (state_in_char) {
    case 'R':
      return CpuThreadData::RUNNING;
    case 'S':
      return CpuThreadData::SLEEPING;
    case 'D':
      return CpuThreadData::WAITING;
    case 'Z':
      return CpuThreadData::ZOMBIE;
    case 'T':
      // TODO: Handle the subtle difference before and afer Linux 2.6.33.
      return CpuThreadData::STOPPED;
    case 't':
      return CpuThreadData::TRACING;
    case 'X':
    case 'x':
      return CpuThreadData::DEAD;
    case 'K':
      return CpuThreadData::WAKEKILL;
    case 'W':
      return CpuThreadData::WAKING;
    case 'P':
      return CpuThreadData::PARKED;
    default:
      return CpuThreadData::UNSPECIFIED;
  }
}

}  // namespace profiler