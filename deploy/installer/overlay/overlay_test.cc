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

#include "tools/base/deploy/installer/overlay/overlay.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <deque>
#include <string>
#include <thread>

#include "tools/base/deploy/common/utils.h"

namespace deploy {

static std::string kBase = "foo/";
static std::string kOverlayFolder = kBase + ".bar/";

static int system(const std::string& cmd) { return ::system(cmd.c_str()); }

static int mkdir(const std::string& path, int args) {
  return ::mkdir(path.c_str(), args);
}

static int access(const std::string& path, int args) {
  return ::access(path.c_str(), args);
}

class OverlayTest : public ::testing::Test {
 public:
  OverlayTest() {}
  void SetUp() override { ASSERT_EQ(0, mkdir(kBase, S_IRWXU)); }

  void TearDown() override { ASSERT_EQ(0, system("rm -rf " + kBase)); }

  void CheckFile(const std::string& path, const std::string& value) {
    std::string content;
    ASSERT_TRUE(deploy::ReadFile(path, &content));
    ASSERT_EQ(value, content);
  }
};

TEST_F(OverlayTest, TestOverlayCreate) {
  ASSERT_FALSE(Overlay::Exists(kOverlayFolder, "id"));

  Overlay overlay(kOverlayFolder, "id");
  ASSERT_TRUE(overlay.Open());
  // Open should create the overlay folder
  ASSERT_EQ(0, access(kOverlayFolder, F_OK));

  ASSERT_TRUE(overlay.Commit());
  // Commit should create the id file
  ASSERT_EQ(0, access(kOverlayFolder + "id", F_OK));

  ASSERT_TRUE(Overlay::Exists(kOverlayFolder, "id"));

  Overlay new_overlay(kOverlayFolder, "new_id");
  ASSERT_TRUE(new_overlay.Open());

  // ID file was deleted.
  ASSERT_NE(0, access(kOverlayFolder + "id", F_OK));

  // If overlay object is already open, can re-open.
  ASSERT_TRUE(new_overlay.Open());

  // Other overlay cannot open already open overlay.
  Overlay other(kOverlayFolder, "new_id");
  ASSERT_FALSE(other.Open());

  ASSERT_TRUE(new_overlay.Commit());
  ASSERT_TRUE(Overlay::Exists(kOverlayFolder, "new_id"));
};

TEST_F(OverlayTest, TestOverlayWriteFile) {
  Overlay overlay(kOverlayFolder, "id");
  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.WriteFile("apk/test.txt", "alpha"));
  ASSERT_TRUE(overlay.WriteFile("apk/sub/other.txt", "beta"));
  ASSERT_TRUE(overlay.WriteFile("other/sub/sub/test.txt", "gamma"));

  ASSERT_EQ(0, access(kOverlayFolder.c_str(), F_OK));
  CheckFile(kOverlayFolder + "apk/test.txt", "alpha");
  CheckFile(kOverlayFolder + "apk/sub/other.txt", "beta");
  CheckFile(kOverlayFolder + "other/sub/sub/test.txt", "gamma");

  ASSERT_TRUE(overlay.Commit());
};

TEST_F(OverlayTest, TestOverlayDeleteFile) {
  Overlay overlay(kOverlayFolder, "id");
  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.WriteFile("apk/test.txt", "alpha"));
  ASSERT_TRUE(overlay.WriteFile("apk/sub/other.txt", "beta"));

  ASSERT_EQ(0, access(kOverlayFolder, F_OK));
  CheckFile(kOverlayFolder + "apk/test.txt", "alpha");
  CheckFile(kOverlayFolder + "apk/sub/other.txt", "beta");

  ASSERT_TRUE(overlay.Commit());

  ASSERT_TRUE(overlay.Open());
  ASSERT_TRUE(overlay.DeleteFile("apk/test.txt"));

  CheckFile(kOverlayFolder + "apk/sub/other.txt", "beta");
  ASSERT_NE(0, access(kOverlayFolder + "apk/test.txt", F_OK));

  ASSERT_TRUE(overlay.Commit());
};

}  // namespace deploy
