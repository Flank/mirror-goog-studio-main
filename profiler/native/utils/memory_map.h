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
#ifndef UTILS_NATIVE_MEMORYMAP_H_
#define UTILS_NATIVE_MEMORYMAP_H_
#include <cstdint>
#include <map>
#include <string>
#include <vector>

#include "utils/procfs_files.h"

namespace profiler {

class MemoryMap final {
 public:
  struct MemoryRegion {
    std::string name;
    uintptr_t start_address;
    uintptr_t end_address;
    uintptr_t file_offset;
    bool contains(uintptr_t addr) {
      return addr >= start_address && addr < end_address;
    }
  };

  MemoryMap(const ProcfsFiles& procfs, int32_t pid)
      : procfs_(procfs), pid_(pid) {}

  bool Update();
  const std::vector<MemoryRegion>& GetRegions();
  const MemoryRegion* LookupRegion(uintptr_t address);

 private:
  const ProcfsFiles& procfs_;
  const int32_t pid_;
  std::vector<MemoryRegion> regions_;
  std::map<uintptr_t, MemoryRegion*> addr_to_region_;
};
}  // namespace profiler
#endif  // UTILS_NATIVE_MEMORYMAP_H_
