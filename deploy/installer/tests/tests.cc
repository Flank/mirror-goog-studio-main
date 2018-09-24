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

#include <gtest/gtest.h>
#include "apk_archive.h"

using namespace deploy;

// Friend test to get around private scope of ApkArchive private functions.
class deploy::ApkArchiveTester {
 public:
  ApkArchiveTester(const ApkArchive& archive) : archive_(archive) {}

  bool Prepare() noexcept { return archive_.Prepare(); }

  ApkArchive::Location GetCDLocation() noexcept {
    return archive_.GetCDLocation();
  }
  ApkArchive::Location GetSignatureLocation(uint8_t* start) noexcept {
    return archive_.GetSignatureLocation(start);
  }

 private:
  ApkArchive archive_;
};

class InstallerTest : public ::testing::Test {};

TEST_F(InstallerTest, TestArchiveParser) {
  ApkArchive archive(
      "tools/base/deploy/installer/tests/data/app/my.fake.app/sample.apk");

  ApkArchiveTester archiveTester(archive);
  EXPECT_TRUE(archiveTester.Prepare());

  ApkArchive::Location cdLoc = archiveTester.GetCDLocation();
  EXPECT_TRUE(cdLoc.valid);

  // Check that block can be retrieved
  ApkArchive::Location sigLoc = archiveTester.GetSignatureLocation(cdLoc.start);
  EXPECT_TRUE(sigLoc.valid);
}
