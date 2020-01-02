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
 *
 */
#include "utils/memory_map.h"

#include <algorithm>
#include "utils/file_reader.h"

using std::string;
using std::uintptr_t;
using std::vector;

namespace profiler {

const std::vector<MemoryMap::MemoryRegion> &MemoryMap::GetRegions() {
  return regions_;
}

bool MemoryMap::Update() {
  vector<string> lines;
  const size_t kTypicalMemoryMapSize = 2000;
  lines.reserve(kTypicalMemoryMapSize);
  string maps_path = procfs_.GetMemoryMapFilePath(pid_);
  if (!FileReader::Read(maps_path, &lines)) {
    return false;
  }
  regions_.clear();
  regions_.reserve(lines.size());

  // /proc/<pid>/maps file contains a sequence of lines in this format:
  // <address range>      <perms> <offset> <dev> <inode>     <pathname>
  // (see more http://man7.org/linux/man-pages/man5/proc.5.html)
  //
  // For example:
  // 00400000-0040b000 r-xp 00000000 fc:01 915813      /bin/cat
  // 0060a000-0060b000 r--p 0000a000 fc:01 915813      /bin/cat
  // 0060b000-0060c000 rw-p 0000b000 fc:01 915813      /bin/cat
  // 01ee7000-01f08000 rw-p 00000000 00:00 0           [heap]
  // 7e0d0d8000-7e0d0d9000 ---p 00000000 00:00 0
  // 7e0d0d9000-7e0d0da000 r--p 00015000 fd:00 2231 /system/lib64/libselinux.so
  // 7e0d0da000-7e0d0db000 rw-p 00016000 fd:00 2231 /system/lib64/libselinux.so
  // 7e0d0db000-7e0d0dc000 rw-p 00000000 00:00 0       [anon:.bss]
  // 7e0d12b000-7e0d13b000 r-xp 00000000 fd:00 2090 /system/lib64/libcutils.so
  // 7e0d13b000-7e0d13d000 r--p 0000f000 fd:00 2090 /system/lib64/libcutils.so
  // 7e0d13d000-7e0d13e000 rw-p 00011000 fd:00 2090 /system/lib64/libcutils.so
  // 7e0d172000-7e0d174000 r-xp 00000000 fd:00 2096    /system/lib64/libdl.so
  // 7e0d174000-7e0d175000 r--p 00001000 fd:00 2096    /system/lib64/libdl.so
  // 7e0d175000-7e0d176000 rw-p 00002000 fd:00 2096    /system/lib64/libdl.so
  // 7e0d176000-7e0d177000 rw-p 00000000 00:00 0       [anon:.bss]
  // 7e0d1a9000-7e0d26c000 r-xp 00000000 fd:00 2073    /system/lib64/libc.so
  // 7e0d26c000-7e0d26d000 ---p 00000000 00:00 0
  // 7e0d26d000-7e0d273000 r--p 000c3000 fd:00 2073    /system/lib64/libc.so
  // 7ffc181cd000-7ffc181cf000 r-xp 00000000 00:00 0   [vdso]
  for (string &line : lines) {
    size_t start_address;
    size_t end_address;
    size_t offset;
    int dev1, dev2, inode;
    vector<char> region_name_buf(line.size(), '\0');
    // Please note a space after <inode>(%d), it has to be there
    // even if the path is empty.
    int tokens_read = sscanf(
        line.c_str(), "%zx-%zx %*[-rwxsp] %zx %x:%x %d %[^\n]", &start_address,
        &end_address, &offset, &dev1, &dev2, &inode, region_name_buf.data());
    if (tokens_read >= 6) {
      bool module_name_is_present = (tokens_read == 7);
      MemoryRegion region{module_name_is_present ? region_name_buf.data() : "",
                          reinterpret_cast<uintptr_t>(start_address),
                          reinterpret_cast<uintptr_t>(end_address),
                          reinterpret_cast<uintptr_t>(offset)};
      regions_.push_back(region);
    }
  }

  std::sort(regions_.begin(), regions_.end(),
            [](const MemoryRegion &l, const MemoryRegion &r) {
              return l.end_address < r.end_address;
            });
  return true;
}

const MemoryMap::MemoryRegion *MemoryMap::LookupRegion(uintptr_t address) {
  auto iter = std::upper_bound(regions_.begin(), regions_.end(), address,
                               [](uintptr_t addr, const MemoryRegion &region) {
                                 return addr < region.end_address;
                               });
  if (iter == regions_.end()) return nullptr;
  const MemoryRegion &region = *iter;
  if (region.contains(address)) {
    return &region;
  }
  return nullptr;
}
}  // namespace profiler
