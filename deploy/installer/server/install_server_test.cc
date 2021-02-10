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

#include "tools/base/deploy/installer/server/install_server.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/server/canary.h"
#include "tools/base/deploy/installer/server/install_client.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <deque>
#include <iostream>
#include <string>
#include <thread>

namespace deploy {

static std::string kOverlayFolder = "./foo/.bar/";

class InstallServerTest : public ::testing::Test {
 public:
  InstallServerTest() = default;

  void SetUp() override {
    // When we create fake appserverd failing to start, we return closed pipes.
    // Writing to a closed pipe will raise a SIGPIPE which we need to ignore.
    signal(SIGPIPE, SIG_IGN);
  }

  void TearDown() override {
    const std::string cmd = "rm -rf " + kOverlayFolder;
    EXPECT_EQ(0, system(cmd.c_str()));
  }
};

class FakeExecutor : public Executor {
 public:
  FakeExecutor(std::thread& server_thread, std::deque<bool>& success)
      : server_thread_(server_thread), success_(success) {}

  bool Run(const std::string& executable_path,
           const std::vector<std::string>& args, std::string* output,
           std::string* error) const {
    bool success = success_.front();
    success_.pop_front();

    if (success) {
      if (output) *output = "Success";
      if (error) *error = "";
    } else {
      if (output) *output = "";
      if (error) *error = "Failure";
    }

    return success;
  }

  bool ForkAndExec(const std::string& executable_path,
                   const std::vector<std::string>& args, int* child_stdin_fd,
                   int* child_stdout_fd, int* child_stderr_fd,
                   int* fork_pid) const {
    bool success = success_.front();
    success_.pop_front();

    int stdin[2], stdout[2], stderr[2];
    pipe(stdin);
    pipe(stdout);
    pipe(stderr);

    if (success) {
      server_thread_ = std::thread([=]() {
        Canary canary("foo");
        InstallServer server(stdin[0], stdout[1], canary);
        server.Run();
        close(stderr[1]);
      });
    } else {
      write(stderr[1], "Failure", 8);
      close(stdin[0]);
      close(stdout[1]);
      close(stderr[1]);
    }

    *child_stdin_fd = stdin[1];
    *child_stdout_fd = stdout[0];
    *child_stderr_fd = stderr[0];

    // ForkAndExec in ExecutorImpl only returns false if the pipe creation
    // fails, so we mirror that behavior here.
    return true;
  }

  bool ForkAndExecWithStdinFd(const std::string& executable_path,
                              const std::vector<std::string>& args,
                              int stdin_fd, int* child_stdout_fd,
                              int* child_stderr_fd, int* fork_pid) const {
    // Not used.
    return false;
  }

  void JoinServerThread() { server_thread_.join(); }

 private:
  // These need to be references because all the methods in executor are const,
  // which means that we wouldn't be able to assign to or update these variables
  // from the Executor interface methods.
  std::thread& server_thread_;
  std::deque<bool>& success_;
};

TEST_F(InstallServerTest, TestServerStartNoOverlay) {
  std::thread server_thread;
  std::deque<bool> success = {true, true};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);
    proto::CheckSetupRequest req;
    EXPECT_TRUE(client.CheckSetup(req) != nullptr);
  }

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestServerStartWithOverlay) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);
  {
    // Start the server and create an overlay
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req1;
    req1.set_overlay_id("id");
    req1.set_overlay_path(kOverlayFolder);

    auto resp1 = client.UpdateOverlay(req1);
    EXPECT_TRUE(resp1 != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp1->status());
  }
  fake_exec.JoinServerThread();

  {
    // Attempt to update with an id mismatch
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req2;
    req2.set_overlay_id("next-id");
    req2.set_overlay_path(kOverlayFolder);
    req2.set_expected_overlay_id("not-id");

    auto resp2 = client.UpdateOverlay(req2);
    EXPECT_TRUE(resp2 != nullptr);
    EXPECT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH, resp2->status());
  }

  fake_exec.JoinServerThread();

  {
    // Update correctly
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req3;
    req3.set_overlay_id("next-id");
    req3.set_overlay_path(kOverlayFolder);
    req3.set_expected_overlay_id("id");

    auto resp3 = client.UpdateOverlay(req3);
    EXPECT_TRUE(resp3 != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp3->status());
    EXPECT_EQ("", resp3->error_message());
  }

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestOverlayEmptyIdCheck) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerResponse foo;

  {
    // Start the server and create an overlay
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req;
    req.set_overlay_id("id");
    req.set_overlay_path(kOverlayFolder);
    auto resp = client.UpdateOverlay(req);

    EXPECT_TRUE(resp != nullptr);
    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp->status());
  }
  fake_exec.JoinServerThread();

  // Attempt to update with an un-set expected id.
  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req1;
    req1.set_overlay_id("next-id");
    req1.set_overlay_path(kOverlayFolder);
    auto resp1 = client.UpdateOverlay(req1);
    EXPECT_TRUE(resp1 != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH, resp1->status());
  }
  fake_exec.JoinServerThread();
}

TEST_F(InstallServerTest, TestServerOverlayFiles) {
  std::thread server_thread;
  std::deque<bool> success = {true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerResponse foo;

  // Start the server and create an overlay
  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req;
    req.set_overlay_id("id");
    req.set_overlay_path(kOverlayFolder);
    proto::OverlayFile* added = req.add_files_to_write();
    added->set_path("apk/hello.txt");
    added->set_content("hello world");

    auto resp = client.UpdateOverlay(req);
    EXPECT_TRUE(resp != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp->status());
  }
  fake_exec.JoinServerThread();

  std::string content;
  EXPECT_TRUE(deploy::ReadFile(kOverlayFolder + "apk/hello.txt", &content));
  EXPECT_EQ("hello world", content);

  {
    // Start the server and overwrite and delete a file
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req1;
    req1.set_expected_overlay_id("id");
    req1.set_overlay_id("next-id");
    req1.set_overlay_path(kOverlayFolder);
    req1.clear_files_to_write();

    proto::OverlayFile* added = req1.add_files_to_write();
    added->set_path("apk/hello_2.txt");
    added->set_content("hello again world");
    req1.add_files_to_delete("apk/hello.txt");

    auto resp1 = client.UpdateOverlay(req1);
    EXPECT_TRUE(resp1 != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp1->status());
  }
  fake_exec.JoinServerThread();

  content.clear();
  EXPECT_FALSE(deploy::ReadFile(kOverlayFolder + "apk/hello.txt", &content));
  EXPECT_TRUE(content.empty());

  content.clear();
  EXPECT_TRUE(deploy::ReadFile(kOverlayFolder + "apk/hello_2.txt", &content));
  EXPECT_EQ("hello again world", content);
}

TEST_F(InstallServerTest, TestNeedCopy) {
  std::thread server_thread;
  // Run fails, copy succeeds, run succeeds.
  std::deque<bool> success = {false, true, true};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::CheckSetupRequest req;
    EXPECT_TRUE(client.CheckSetup(req) != nullptr);
  }

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestNeedMkdir) {
  std::thread server_thread;
  // Run fails, copy fails, mkdir succeeds, copy succeeds, run succeeds.
  std::deque<bool> success = {false, false, true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::CheckSetupRequest req;
    EXPECT_TRUE(client.CheckSetup(req) != nullptr);
  }

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestCopyAndMkdirFails) {
  std::thread server_thread;
  // #1 Run fails
  // #2 Copy fails, mkdir fails, copy fails.
  // #3 Run fails
  std::deque<bool> success = {false, false, false, false, false};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);
    proto::CheckSetupRequest req;
    client.CheckSetup(req);
  }

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
};

// The odd case that the first copy fails, but the directory already exists, and
// the second copy is ok.
TEST_F(InstallServerTest, TestMkdirFailCopySucceedss) {
  std::thread server_thread;
  // #1 Run fails
  // #2 copy fails, mkdir fails, copy succeeds
  // #3 run succeeds.
  std::deque<bool> success = {false, false, false, true, true};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::CheckSetupRequest req;
    client.CheckSetup(req);
  }
  fake_exec.JoinServerThread();

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
};

TEST_F(InstallServerTest, TestAllStartsFail) {
  std::thread server_thread;
  // Run fails, copy succeeds, run fails.
  std::deque<bool> success = {false, true, false};
  FakeExecutor fake_exec(server_thread, success);

  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);
    proto::CheckSetupRequest req;
    client.CheckSetup(req);
  }
  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
};

// b/179035177
TEST_F(InstallServerTest, DISABLED_FlushLiveLiteralDex) {
  std::thread server_thread;
  std::deque<bool> success = {true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerResponse foo;

  // Start the server and create an overlay
  {
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req;
    req.set_overlay_id("id");
    req.set_overlay_path(kOverlayFolder);
    proto::OverlayFile* added = req.add_files_to_write();
    added->set_path("apk/hello.txt");
    added->set_content("hello world");

    auto resp = client.UpdateOverlay(req);
    EXPECT_TRUE(resp != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp->status());
  }

  fake_exec.JoinServerThread();

  std::string content;
  EXPECT_TRUE(deploy::ReadFile(kOverlayFolder + "apk/hello.txt", &content));
  EXPECT_EQ("hello world", content);

  // Pretend there was a LL request and we wrote some dex to persist.
  std::string dex = "fake dex content";
  EXPECT_TRUE(IO::mkpath(kOverlayFolder + ".ll/", 0777));
  EXPECT_TRUE(deploy::WriteFile(kOverlayFolder + ".ll/a.dex", dex));
  EXPECT_TRUE(deploy::WriteFile(kOverlayFolder + ".ll/b.dex", dex));

  {
    // Start the server and overwrite and delete a file
    auto client = InstallClient("fakepkg", "fakepath", "iwi", fake_exec);

    proto::OverlayUpdateRequest req1;
    req1.set_expected_overlay_id("id");
    req1.set_overlay_id("next-id");
    req1.set_overlay_path(kOverlayFolder);
    req1.clear_files_to_write();

    proto::OverlayFile* added = req1.add_files_to_write();
    added->set_path("apk/hello_2.txt");
    added->set_content("hello again world");
    req1.add_files_to_delete("apk/hello.txt");

    auto resp1 = client.UpdateOverlay(req1);
    EXPECT_TRUE(resp1 != nullptr);

    EXPECT_EQ(proto::OverlayUpdateResponse::OK, resp1->status());
  }

  fake_exec.JoinServerThread();

  content.clear();
  EXPECT_FALSE(deploy::ReadFile(kOverlayFolder + "apk/hello.txt", &content));
  EXPECT_TRUE(content.empty());

  content.clear();
  EXPECT_TRUE(deploy::ReadFile(kOverlayFolder + "apk/hello_2.txt", &content));
  EXPECT_EQ("hello again world", content);

  // Make sure LL directory was nuked.
  DIR* dir = opendir((kOverlayFolder + ".ll/").c_str());
  EXPECT_TRUE(ENOENT == errno);
  closedir(dir);
};

}  // namespace deploy
