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

#include "tools/base/deploy/installer/install_server.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/installer/workspace.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <deque>
#include <string>
#include <thread>

namespace deploy {

class InstallServerTest : public ::testing::Test {
 public:
  InstallServerTest() = default;

  void TearDown() override { ASSERT_EQ(0, system("rm -rf .overlay")); }
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
      *output = "Success";
      *error = "";
    } else {
      *output = "";
      *error = "Failure";
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
        InstallServer server(stdin[0], stdout[1]);
        server.Run();
        close(stdin[0]);
        close(stdout[1]);
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
  std::deque<bool> success = {true};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  ASSERT_TRUE(client->Write(request));
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestServerStartWithOverlay) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("id");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();

  // Attempt to update with an id mismatch
  client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->set_expected_overlay_id("not-id");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();

  // Update correctly
  client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->set_expected_overlay_id("id");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_EQ("", response.overlay_response().error_message());
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestOverlayEmptyIdCheck) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("id");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();

  // Attempt to update with an un-set expected id.
  client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  fake_exec.JoinServerThread();
}

TEST_F(InstallServerTest, TestServerOverlayFiles) {
  std::thread server_thread;
  std::deque<bool> success = {true, true};
  FakeExecutor fake_exec(server_thread, success);

  // // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("id");
  proto::OverlayFile* added =
      request.mutable_overlay_request()->add_added_files();
  added->set_path("apk/hello.txt");
  added->set_content("hello world");

  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  std::string content;
  ASSERT_TRUE(deploy::ReadFile(".overlay/apk/hello.txt", &content));
  ASSERT_EQ("hello world", content);

  fake_exec.JoinServerThread();

  // Start the server and overwrite and delete a file
  client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_expected_overlay_id("id");
  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->clear_added_files();
  added = request.mutable_overlay_request()->add_added_files();
  added->set_path("apk/hello_2.txt");
  added->set_content("hello again world");
  request.mutable_overlay_request()->add_deleted_files("apk/hello.txt");
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->Read(-1, &response));
  ASSERT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  ASSERT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  ASSERT_TRUE(client->WaitForExit());

  content.clear();
  ASSERT_FALSE(deploy::ReadFile(".overlay/apk/hello.txt", &content));
  ASSERT_TRUE(content.empty());

  content.clear();
  ASSERT_TRUE(deploy::ReadFile(".overlay/apk/hello_2.txt", &content));
  ASSERT_EQ("hello again world", content);

  fake_exec.JoinServerThread();
}

TEST_F(InstallServerTest, TestNeedCopy) {
  std::thread server_thread;
  // Run fails, copy succeeds, run succeeds.
  std::deque<bool> success = {false, true, true};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  ASSERT_TRUE(client->Write(request));

  ASSERT_TRUE(client->WaitForExit());

  // Make sure we consumed all the exec results.
  ASSERT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestCopyFails) {
  std::thread server_thread;
  // Run fails, copy fails.
  std::deque<bool> success = {false, false};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_TRUE(nullptr == client);

  // Make sure we consumed all the exec results.
  ASSERT_TRUE(success.empty());
};

TEST_F(InstallServerTest, TestAllStartsFail) {
  std::thread server_thread;
  // Run fails, copy succeeds, run fails.
  std::deque<bool> success = {false, true, false};
  FakeExecutor fake_exec(server_thread, success);

  // Don't call workspace init; it closes stdout.
  Workspace workspace("/path/to/fake/installer", "fakeversion", &fake_exec);

  auto client = StartServer(workspace, "fakepath", "fakepackage");
  ASSERT_TRUE(nullptr == client);

  // Make sure we consumed all the exec results.
  ASSERT_TRUE(success.empty());
};

}  // namespace deploy
