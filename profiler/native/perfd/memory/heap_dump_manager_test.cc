/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "heap_dump_manager.h"

#include <fstream>
#include <sstream>

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include "utils/count_down_latch.h"
#include "utils/device_info.h"
#include "utils/device_info_helper.h"
#include "utils/file_cache.h"

using ::testing::_;
using ::testing::Return;

namespace profiler {

class MockAcitivtyManager final : public ActivityManager {
 public:
  explicit MockAcitivtyManager()
      : ActivityManager(
            std::unique_ptr<BashCommandRunner>(new BashCommandRunner("blah"))) {
  }
  MOCK_CONST_METHOD3(TriggerHeapDump,
                     bool(int pid, const std::string &file_path,
                          std::string *error_string));
};

// Test that:
// 1. A heap dump can start successfully if none is in progress
// 2. Another heap dump cannot be started in between
// 3. Upon the dump file finished being written to, the heap dump thread ends.
TEST(HeapDumpManager, DumpOnODevice) {
  // Fake a O device and real file system so we can test the O retry workflow.
  DeviceInfoHelper::SetDeviceInfo(DeviceInfo::O);
  profiler::FileCache file_cache(getenv("TEST_TMPDIR"));
  int64_t dump_id = 1;
  std::stringstream ss;
  ss << dump_id;
  std::string file_name = ss.str();
  // First create an empty file so that the dumper has something to read from.
  file_cache.AddChunk(file_name, "");
  file_cache.Complete(file_name);

  MockAcitivtyManager activity_manager;
  EXPECT_CALL(activity_manager, TriggerHeapDump(_, _, _))
      .WillRepeatedly(Return(true));
  HeapDumpManager dump(&file_cache, &activity_manager);

  CountDownLatch latch(1);
  bool result = dump.TriggerHeapDump(1, dump_id, [&latch](bool dump_result) {
    EXPECT_TRUE(dump_result);
    latch.CountDown();
  });
  EXPECT_TRUE(result);

  // Sanity-check: trigger another heap dump before one is finish is disallowed.
  EXPECT_FALSE(dump.TriggerHeapDump(1, dump_id, nullptr));

  // Inserts content to the dump file that fakes that the dump is complete.
  // Heap dumper expects content to be greater than |kHprofEndTagLength|.
  // Everything is expected to be zero except the last |kHprofDumpEndTag|th
  // byte which should be |kHprofDumpEndTag|
  std::string dump_content(HeapDumpManager::kHprofEndTagLength + 1, 0x00);
  dump_content[1] = HeapDumpManager::kHprofDumpEndTag;
  auto file = file_cache.GetFile(file_name);
  file->OpenForWrite();
  file->Append(dump_content);
  file->Close();
  latch.Await();

  // We should now be able to trigger a dump again. We read from the same
  // dump id so we should expect a successful result in the callback.
  CountDownLatch latch2(1);
  result = dump.TriggerHeapDump(1, dump_id, [&latch2](bool dump_result) {
    EXPECT_TRUE(dump_result);
    latch2.CountDown();
  });
  EXPECT_TRUE(result);
  latch2.Await();
}

}  // namespace profiler
