#include "tracing_utils.h"

#include <stdlib.h>
#include <string>
#include "utils/fs/disk_file_system.h"

namespace profiler {
// Note: The it's unclear when the non-debug pipes will be used. In production
// builds (on both Pixel, and Samsung) the debug pipe is always used.
const char *kTracingFileNames[] = {"/sys/kernel/debug/tracing/tracing_on",
                                   "/sys/kernel/tracing/tracing_on"};

const char *kTracingBufferFileNames[] = {
    "/sys/kernel/debug/tracing/buffer_size_kb",
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

void TracingUtils::ForceStopTracer() {
  WriteIntToConfigFile(kTracingFileNames,
                       sizeof(kTracingFileNames) / sizeof(kTracingFileNames[0]),
                       0);
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

void TracingUtils::WriteIntToConfigFile(const char *files[], uint32_t count,
                                        uint32_t value) {
  DiskFileSystem fs;
  for (uint32_t i = 0; i < count; i++) {
    if (fs.Write(files[i], "0")) {
      return;
    }
  }
}
}  // namespace profiler