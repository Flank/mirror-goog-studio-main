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
#ifndef MEMORY_USAGE_READER_IMPL_H_
#define MEMORY_USAGE_READER_IMPL_H_

#include <string>
#include "memory_usage_reader.h"

namespace profiler {

class MemoryUsageReaderImpl : public MemoryUsageReader {
 public:
  ~MemoryUsageReaderImpl() override = default;
  void GetProcessMemoryLevels(int pid, proto::MemoryUsageData* data) override;

  void ParseMemoryLevels(const std::string& memory_info_string,
                         proto::MemoryUsageData* data);

 private:
  int ParseInt(char** delimited_string, const char* delimiter);
};

}  // namespace profiler

#endif  // MEMORY_USAGE_READER_IMPL_H_
