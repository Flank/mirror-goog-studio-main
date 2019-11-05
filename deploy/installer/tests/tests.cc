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

#include <fstream>
#include <iostream>

#include <gtest/gtest.h>

#include "tools/base/deploy/installer/apk_archive.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor_impl.h"
#include "tools/base/deploy/installer/patch_applier.h"
#include "tools/base/deploy/proto/deploy.pb.h"

using namespace deploy;

// Friend test to get around private scope of ApkArchive private functions.
class deploy::ApkArchiveTester {
 public:
  ApkArchiveTester(const ApkArchive& archive) : archive_(archive) {}

  ApkArchive::Location GetCDLocation() noexcept {
    return archive_.GetCDLocation();
  }
  ApkArchive::Location GetSignatureLocation(size_t start) noexcept {
    return archive_.GetSignatureLocation(start);
  }

 private:
  ApkArchive archive_;
};

class GetProcessInfoExecutor : public Executor {
 public:
  GetProcessInfoExecutor(const std::string& file_path)
      : file_path_(file_path) {}

  bool Run(const std::string& executable_path,
           const std::vector<std::string>& args, std::string* output,
           std::string* error) const {
    std::ifstream file(file_path_);
    std::string line;
    while (std::getline(file, line)) {
      output->append(line);
      output->append("\n");
    }
    std::cout << *output << std::endl;
    return true;
  }

  bool ForkAndExec(const std::string& executable_path,
                   const std::vector<std::string>& parameters,
                   int* child_stdin_fd, int* child_stdout_fd,
                   int* child_stderr_fd, int* fork_pid) const {
    return false;
  }

  bool ForkAndExecWithStdinFd(const std::string& executable_path,
                              const std::vector<std::string>& parameters,
                              int stdin_fd, int* child_stdout_fd,
                              int* child_stderr_fd, int* fork_pid) const {
    return false;
  }

 private:
  const std::string file_path_;
};

class InstallerTest : public ::testing::Test {};

TEST_F(InstallerTest, TestGetProcessInfo) {
  Executor* exec = new GetProcessInfoExecutor(
      "tools/base/deploy/installer/tests/data/dumpOutput.txt");
  Workspace workspace("fake/path", "fakeversion", exec);
  CmdCommand cmd(workspace);

  std::vector<ProcessRecord> records;
  ASSERT_TRUE(cmd.GetProcessInfo("com.noah.clr", &records));
  ASSERT_EQ(2, records.size());
  ASSERT_EQ("com.noah.clr:wow", records[0].process_name);
  ASSERT_EQ(false, records[0].crashing);
  ASSERT_EQ(true, records[0].not_responding);

  ASSERT_EQ("com.noah.clr", records[1].process_name);
  ASSERT_EQ(true, records[1].crashing);
  ASSERT_EQ(false, records[1].not_responding);
}

TEST_F(InstallerTest, TestArchiveParser) {
  ApkArchive archive(
      "tools/base/deploy/installer/tests/data/app/my.fake.app/sample.apk");

  ApkArchiveTester archiveTester(archive);

  ApkArchive::Location cdLoc = archiveTester.GetCDLocation();
  EXPECT_TRUE(cdLoc.valid);
  ASSERT_EQ(cdLoc.offset, 2044145);
  ASSERT_EQ(cdLoc.size, 49390);

  // Check that block can be retrieved
  ApkArchive::Location sigLoc =
      archiveTester.GetSignatureLocation(cdLoc.offset);
  EXPECT_TRUE(sigLoc.valid);
  ASSERT_EQ(sigLoc.offset, 2040049);
  ASSERT_EQ(sigLoc.size, 4088);
}

TEST_F(InstallerTest, TestFileNoOpPatching) {
  proto::PatchInstruction patchInstruction;
  patchInstruction.set_src_absolute_path(
      "tools/base/deploy/installer/tests/data/patchTest.txt");
  int32_t instructions[0] = {};
  patchInstruction.set_instructions(reinterpret_cast<char*>(instructions), 0);
  int32_t patches[0] = {};
  patchInstruction.set_patches(reinterpret_cast<char*>(patches), 0);
  patchInstruction.set_dst_filesize(3);

  int pipeBuffer[2];
  pipe(pipeBuffer);
  PatchApplier patchApplier("");
  bool patchSucceeded =
      patchApplier.ApplyPatchToFD(patchInstruction, pipeBuffer[1]);
  EXPECT_TRUE(patchSucceeded == true);
  close(pipeBuffer[1]);

  char patchedContent[3] = {'z', 'z', 'z'};
  read(pipeBuffer[0], patchedContent, 3);
  close(pipeBuffer[0]);

  EXPECT_TRUE(patchedContent[0] == 'c');
  EXPECT_TRUE(patchedContent[1] == 'b');
  EXPECT_TRUE(patchedContent[2] == 'a');
}

TEST_F(InstallerTest, TestFilePatchingDirtyBeginning) {
  proto::PatchInstruction patchInstruction;
  patchInstruction.set_src_absolute_path(
      "tools/base/deploy/installer/tests/data/patchTest.txt");
  // Patch index 0 with 1 byte from patch payload.
  int32_t instructions[2] = {0, 1};
  patchInstruction.set_instructions(reinterpret_cast<char*>(instructions), 8);
  const char* patches = "a";
  patchInstruction.set_patches(patches, 1);
  patchInstruction.set_dst_filesize(3);

  int pipeBuffer[2];
  pipe(pipeBuffer);
  PatchApplier patchApplier("");
  patchApplier.ApplyPatchToFD(patchInstruction, pipeBuffer[1]);
  close(pipeBuffer[1]);

  char patchedContent[3] = {'z', 'z', 'z'};
  read(pipeBuffer[0], patchedContent, 3);
  close(pipeBuffer[0]);

  EXPECT_TRUE(patchedContent[0] == 'a');
  EXPECT_TRUE(patchedContent[1] == 'b');
  EXPECT_TRUE(patchedContent[2] == 'a');
}

TEST_F(InstallerTest, TestFilePatching) {
  proto::PatchInstruction patchInstruction;
  patchInstruction.set_src_absolute_path(
      "tools/base/deploy/installer/tests/data/patchTest.txt");
  // Patch index 0 with 1 byte from patch payload.
  // Patch index 2 with 1 byte from patch payload.
  int32_t instructions[4] = {0, 1, 2, 1};
  patchInstruction.set_instructions(reinterpret_cast<char*>(instructions), 16);
  const char* patches = "ac";
  patchInstruction.set_patches(patches, 2);
  patchInstruction.set_dst_filesize(3);

  int pipeBuffer[2];
  pipe(pipeBuffer);
  PatchApplier patchApplier("");
  patchApplier.ApplyPatchToFD(patchInstruction, pipeBuffer[1]);
  close(pipeBuffer[1]);

  char patchedContent[3] = {'z', 'z', 'z'};
  read(pipeBuffer[0], patchedContent, 3);
  close(pipeBuffer[0]);

  EXPECT_TRUE(patchedContent[0] == 'a');
  EXPECT_TRUE(patchedContent[1] == 'b');
  EXPECT_TRUE(patchedContent[2] == 'c');
}
