#include "tracing_utils.h"

#include <stdlib.h>
#include <string>
#include "utils/fs/disk_file_system.h"

namespace profiler {
const char *kTracingFileNames[] = {"/sys/kernel/debug/tracing/tracing_on",
                                   // Legacy tracing file name.
                                   "/sys/kernel/tracing/tracing_on"};

const char *kTracingBufferFileNames[] = {
    "/sys/kernel/debug/tracing/buffer_size_kb",
    // Legacy tracing file name.
    "/sys/kernel/tracing/buffer_size_kb"};

bool TracingUtils::IsTracerRunning() {
  return ReadIntFromConfigFile(
             kTracingFileNames,
             sizeof(kTracingFileNames) / sizeof(kTracingFileNames[0])) == 1;
}

int TracingUtils::GetTracingBufferSize() {
  return ReadIntFromConfigFile(
      kTracingBufferFileNames,
      sizeof(kTracingBufferFileNames) / sizeof(kTracingBufferFileNames[0]));
}

int TracingUtils::ReadIntFromConfigFile(const char *files[], uint32_t count) {
  DiskFileSystem fs;
  for (uint32_t i = 0; i < count; i++) {
    std::string contents = fs.GetFileContents(files[i]);
    // Only need to return the value of the first file with a value.
    // The second file is assumed to be for older versions of android.
    if (!contents.empty()) {
      return atoi(contents.c_str());
    }
  }
  return -1;
}
}  // namespace profiler