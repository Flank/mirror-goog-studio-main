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

#include "tools/base/deploy/installer/overlay.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <deque>
#include <string>
#include <thread>

#include "tools/base/deploy/common/utils.h"

namespace deploy {

class OverlayTest : public ::testing::Test {
 public:
  OverlayTest() {}
  void SetUp() override { ASSERT_EQ(0, mkdir("code_cache", S_IRWXU)); }

  void TearDown() override { ASSERT_EQ(0, system("rm -rf code_cache")); }

  void CheckFile(const std::string& path, const std::string& value) {
    std::string content;
    ASSERT_TRUE(deploy::ReadFile(path, &content));
    ASSERT_EQ(value, content);
  }
};

TEST_F(OverlayTest, TestOverlayCreate) {
  ASSERT_FALSE(Overlay::Exists("code_cache/.overlay", "id"));

  Overlay overlay("code_cache/.overlay", "id");
  ASSERT_TRUE(overlay.Open());
  // Open should create the overlay folder
  ASSERT_EQ(0, access("code_cache/.overlay", F_OK));

  ASSERT_TRUE(overlay.Commit());
  // Commit should create the id file
  ASSERT_EQ(0, access("code_cache/.overlay/id", F_OK));

  ASSERT_TRUE(Overlay::Exists("code_cache/.overlay", "id"));

  Overlay new_overlay("code_cache/.overlay", "new_id");
  ASSERT_TRUE(new_overlay.Open());

  // ID file was deleted.
  ASSERT_NE(0, access("code_cache/.overlay/id", F_OK));

  // If overlay object is already open, can re-open.
  ASSERT_TRUE(new_overlay.Open());

  // Other overlay cannot open already open overlay.
  Overlay other("code_cache/.overlay", "new_id");
  ASSERT_FALSE(other.Open());

  ASSERT_TRUE(new_overlay.Commit());
  ASSERT_TRUE(Overlay::Exists("code_cache/.overlay", "new_id"));
};

TEST_F(OverlayTest, TestOverlayWriteFile) {
  Overlay overlay("code_cache/.overlay", "id");
  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.WriteFile("apk/test.txt", "alpha"));
  ASSERT_TRUE(overlay.WriteFile("apk/sub/other.txt", "beta"));
  ASSERT_TRUE(overlay.WriteFile("other/sub/sub/test.txt", "gamma"));

  ASSERT_EQ(0, access("code_cache/.overlay", F_OK));
  CheckFile("code_cache/.overlay/apk/test.txt", "alpha");
  CheckFile("code_cache/.overlay/apk/sub/other.txt", "beta");
  CheckFile("code_cache/.overlay/other/sub/sub/test.txt", "gamma");

  ASSERT_TRUE(overlay.Commit());
};

TEST_F(OverlayTest, TestOverlayDeleteFile) {
  Overlay overlay("code_cache/.overlay", "id");
  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.WriteFile("apk/test.txt", "alpha"));
  ASSERT_TRUE(overlay.WriteFile("apk/sub/other.txt", "beta"));

  ASSERT_EQ(0, access("code_cache/.overlay", F_OK));
  CheckFile("code_cache/.overlay/apk/test.txt", "alpha");
  CheckFile("code_cache/.overlay/apk/sub/other.txt", "beta");

  ASSERT_TRUE(overlay.Commit());

  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.DeleteFile("apk/test.txt"));

  CheckFile("code_cache/.overlay/apk/sub/other.txt", "beta");
  ASSERT_NE(0, access("code_cache/.overlay/apk/test.txt", F_OK));

  ASSERT_TRUE(overlay.Commit());
};

}  // namespace deploy
