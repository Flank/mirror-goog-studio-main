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
#include "memory_usage_reader_impl.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <memory>

#include "utils/trace.h"

namespace {
// dumpsys meminfo command that returns a comma-delimited string within calling
// process.
const char* kDumpsysCommandFormat = "dumpsys meminfo --local --checkin %d";
const int kCommandMaxLength = 128;
const int kBufferSize = 1024;

enum MemoryType {
  UNKNOWN,
  PRIVATE_CLEAN,
  PRIVATE_DIRTY,
  ART,
  STACK,
  GRAPHICS,
  CODE,
  OTHERS
};

bool IsOneOf(const char* subject,
             std::initializer_list<const char*> toCompare) {
  return std::any_of(toCompare.begin(), toCompare.end(),
                     [=](const char* s) { return strcmp(subject, s) == 0; });
}

int ParseInt(char** delimited_string, const char* delimiter) {
  char* result = strsep(delimited_string, delimiter);
  if (result == nullptr) {
    return 0;
  } else {
    return strtol(result, nullptr, 10);
  }
}
}  // namespace

namespace profiler {

void MemoryUsageReaderImpl::GetProcessMemoryLevels(
    int pid, proto::MemoryUsageData* data) {
  Trace trace("MEM:GetProcessMemoryLevels");
  char buffer[kBufferSize];
  char cmd[kCommandMaxLength];
  int num_written =
      snprintf(cmd, kCommandMaxLength, kDumpsysCommandFormat, pid);
  if (num_written >= kCommandMaxLength) {
    return;  // TODO error handling.
  }

  // TODO: Use BashCommand object which provide central access point to
  // popen and also shows traces in systrace.
  std::string output = "";
  std::unique_ptr<FILE, int (*)(FILE*)> mem_info_file(popen(cmd, "r"), pclose);
  if (!mem_info_file) {
    return;  // TODO error handling.
  }

  // Skip lines until actual data. Note that before N, "--checkin" is not an
  // official flag
  // so the arg parsing logic complains about invalid arguments.
  do {
    if (feof(mem_info_file.get())) {
      return;  // TODO error handling.
    }
    fgets(buffer, kBufferSize, mem_info_file.get());

    // Skip ahead until the header, which is in the format of: "time, (uptime),
    // (realtime)".
  } while (strncmp(buffer, "time,", 5) != 0);

  // Gather the remaining content which should be a comma-delimited string.
  while (!feof(mem_info_file.get()) &&
         fgets(buffer, kBufferSize, mem_info_file.get()) != nullptr) {
    output += buffer;
  }

  return ParseMemoryLevels(output, data);
}

void MemoryUsageReaderImpl::ParseMemoryLevels(
    const std::string& memory_info_string, proto::MemoryUsageData* data) {
  Trace trace("MEM:ParseMemoryLevels");
  std::unique_ptr<char, void (*)(void*)> delimited_memory_info(
      strdup(memory_info_string.c_str()), std::free);
  char* temp_memory_info_ptr = delimited_memory_info.get();
  char* result;

  int32_t java_private = 0, native_private = 0, stack = 0, graphics = 0,
          code = 0, total = 0;

  // Version check.
  int version = ParseInt(&temp_memory_info_ptr, ",");
  int regularStatsFieldCount = 4;
  constexpr int kTotalIndex = 18;  // index for total memory consumption.
  constexpr int kPrivateDirtyStartIndex =
      30;  // index before the private dirty category begins.
  constexpr int kPrivateCleanStartIndex =
      34;  // index before the private clean category begins.
  int otherStatsFieldCount;
  int otherStatsStartIndex;
  if (version == 4) {
    // New categories (e.g. swappable memory) have been inserted before the
    // other stats categories
    // compared to version 3, so we only have to move forward the
    // otherStatsStartIndex.
    otherStatsStartIndex = 47;
    otherStatsFieldCount = 8;
  } else if (version == 3) {
    otherStatsStartIndex = 39;
    otherStatsFieldCount = 6;
  } else {
    // Older versions predating Kitkat are unsupported - early return.
    return;
  }

  // The logic below extracts the private clean+dirty memory from the
  // comma-delimited string,
  // which starts with: (the capitalized fields above are the ones we need)
  //   {version (parsed above), pid, process_name,}
  // then in groups of 4, the main heap info: (e.g. pss, shared dirty/clean,
  // private dirty/clean)
  //    {NATIVE, DALVIK, other, total,}
  // follow by the other stats, in groups of the number defined in
  // otherStatsFieldCount:
  //    {stats_label, total_pss, swappable_pss, shared_dirty, shared_clean,
  //    PRIVATE_DIRTY,
  //     PRIVATE_CLEAN,...}
  //
  // Note that the total private memory from this format is slightly less than
  // the human-readable
  // dumpsys meminfo version, as that accounts for a small amount of "unknown"
  // memory where the
  // "--checkin" version does not.
  int currentIndex = 0;
  auto accumulate = [&temp_memory_info_ptr](int& target) {
    target += ParseInt(&temp_memory_info_ptr, ",");
  };
  auto skip = [&temp_memory_info_ptr]() { strsep(&temp_memory_info_ptr, ","); };
  while (true) {
    result = strsep(&temp_memory_info_ptr, ",");
    currentIndex++;
    if (result == nullptr) {
      // Reached the end of the output.
      break;
    }

    int memory_type = UNKNOWN;
    if (currentIndex >= otherStatsStartIndex) {
      if (IsOneOf(result, {"Dalvik Other", "Ashmem", "Cursor", "Other dev",
                           "Other mmap", "Other mtrack", "Unknown"})) {
        memory_type = OTHERS;
      } else if (IsOneOf(result, {"Stack"})) {
        memory_type = STACK;
      } else if (IsOneOf(result, {".art mmap"})) {
        memory_type = ART;
      } else if (IsOneOf(result, {"Gfx dev", "EGL mtrack", "GL mtrack"})) {
        memory_type = GRAPHICS;
      } else if (IsOneOf(result, {".so mmap", ".jar mmap", ".apk mmap",
                                  ".ttf mmap", ".dex mmap", ".oat mmap"})) {
        memory_type = CODE;
      }
    } else if (currentIndex == kPrivateCleanStartIndex) {
      memory_type = PRIVATE_CLEAN;
    } else if (currentIndex == kPrivateDirtyStartIndex) {
      memory_type = PRIVATE_DIRTY;
    } else if (currentIndex == kTotalIndex) {
      total = strtol(result, nullptr, 10);
    }

    if (memory_type == PRIVATE_CLEAN) {
      skip();                     // native private clean.
      skip();                     // dalvik private clean.
      skip();                     // UNUSED - other private clean total.
      skip();                     // UNUSED - total private clean.
      currentIndex += regularStatsFieldCount;
    } else if (memory_type == PRIVATE_DIRTY) {
      accumulate(native_private);  // native private dirty.
      accumulate(java_private);    // dalvik private dirty.
      skip();  // UNUSED - other private dirty are tracked separately.
      skip();  // UNUSED - total private dirty.
      currentIndex += regularStatsFieldCount;
    } else if (memory_type != UNKNOWN) {
      skip();  // UNUSED - total pss.
      skip();  // UNUSED - pss clean.
      skip();  // UNUSED - shared dirty.
      skip();  // UNUSED - shared clean.

      // Parse out private dirty and private clean.
      switch (memory_type) {
        case OTHERS:
          skip();
          skip();
          break;
        case STACK:
          accumulate(stack);
          // Note that stack's private clean is treated as private others in
          // dumpsys.
          skip();
          break;
        case ART:
          accumulate(java_private);
          accumulate(java_private);
          break;
        case GRAPHICS:
          accumulate(graphics);
          accumulate(graphics);
          break;
        case CODE:
          accumulate(code);
          accumulate(code);
          break;
      }

      currentIndex += otherStatsFieldCount;
    }
  }

  data->set_java_mem(java_private);
  data->set_native_mem(native_private);
  data->set_stack_mem(stack);
  data->set_graphics_mem(graphics);
  data->set_code_mem(code);
  data->set_others_mem(total - java_private - native_private - stack -
                       graphics - code);
  data->set_total_mem(total);
  return;
}

}  // namespace profiler
