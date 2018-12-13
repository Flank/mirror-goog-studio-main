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

#include <dirent.h>  // dor opendir()
#include <cstdlib>   // for atoi()
#include <cstring>   // for strncmp()
#include <sstream>   // for std::ostringstream
#include <string>
#include <vector>

#include "proto/cpu_data.pb.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/procfs_files.h"

namespace profiler {

using std::string;
using std::vector;

bool GetThreads(const ProcfsFiles* procfs, int32_t pid, vector<int32_t>* tids) {
  vector<string> dir_entries;
  DIR* dp;
  struct dirent* dirp;
  // List thread ID files under /proc/[pid]/task/ directory.
  const string& dir = procfs->GetProcessTaskDir(pid);
  if ((dp = opendir(dir.c_str())) == nullptr) {
    profiler::Log::E("Failed to open dir %s: %s.", dir.c_str(),
                     strerror(errno));
    return false;
  }
  while ((dirp = readdir(dp)) != nullptr) {
    const char* const name = dirp->d_name;
    if (strncmp(name, ".", 1) != 0 && strncmp(name, "..", 2) != 0) {
      // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
      tids->push_back(atoi(name));
    }
  }
  closedir(dp);
  return true;
}

bool GetThreadState(const ProcfsFiles* procfs, int32_t pid, int32_t tid,
                    profiler::proto::CpuThreadData::State* state,
                    string* name) {
  string buffer;
  // Reads /proc/[pid]/task/[tid]/stat file.
  const string& thread_stat_file = procfs->GetThreadStatFilePath(pid, tid);
  if (profiler::FileReader::Read(thread_stat_file, &buffer)) {
    char state_in_char;
    if (ParseThreadStat(tid, buffer, &state_in_char, name)) {
      profiler::proto::CpuThreadData::State enum_state =
          ThreadStateInEnum(state_in_char);
      if (enum_state != profiler::proto::CpuThreadData::UNSPECIFIED) {
        *state = enum_state;
        return true;
      }
    }
  }
  profiler::Log::E("Failed to parse stat file %s: %s", thread_stat_file.c_str(),
                   strerror(errno));
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

profiler::proto::CpuThreadData::State ThreadStateInEnum(char state_in_char) {
  switch (state_in_char) {
    case 'R':
      return profiler::proto::CpuThreadData::RUNNING;
    case 'S':
      return profiler::proto::CpuThreadData::SLEEPING;
    case 'D':
      return profiler::proto::CpuThreadData::WAITING;
    case 'Z':
      return profiler::proto::CpuThreadData::ZOMBIE;
    case 'T':
      // TODO: Handle the subtle difference before and afer Linux 2.6.33.
      return profiler::proto::CpuThreadData::STOPPED;
    case 't':
      return profiler::proto::CpuThreadData::TRACING;
    case 'X':
    case 'x':
      return profiler::proto::CpuThreadData::DEAD;
    case 'K':
      return profiler::proto::CpuThreadData::WAKEKILL;
    case 'W':
      return profiler::proto::CpuThreadData::WAKING;
    case 'P':
      return profiler::proto::CpuThreadData::PARKED;
    default:
      return profiler::proto::CpuThreadData::UNSPECIFIED;
  }
}

}  // namespace profiler