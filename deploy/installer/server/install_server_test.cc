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
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/executor.h"

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

  void TearDown() override { EXPECT_EQ(0, system("rm -rf .overlay")); }
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

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;
  EXPECT_TRUE(client->Write(request));
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestServerStartWithOverlay) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  request.mutable_overlay_request()->set_overlay_id("id");
  request.mutable_overlay_request()->set_overlay_path(".");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();

  // Attempt to update with an id mismatch
  client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->set_expected_overlay_id("not-id");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();

  // Update correctly
  client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->set_expected_overlay_id("id");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_EQ("", response.overlay_response().error_message());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestOverlayEmptyIdCheck) {
  std::thread server_thread;
  std::deque<bool> success = {true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  request.mutable_overlay_request()->set_overlay_id("id");
  request.mutable_overlay_request()->set_overlay_path(".");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();

  // Attempt to update with an un-set expected id.
  client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_overlay_id("next-id");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::ID_MISMATCH,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  fake_exec.JoinServerThread();
}

TEST_F(InstallServerTest, TestServerOverlayFiles) {
  std::thread server_thread;
  std::deque<bool> success = {true, true};
  FakeExecutor fake_exec(server_thread, success);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;

  // Start the server and create an overlay
  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  request.mutable_overlay_request()->set_overlay_id("id");
  request.mutable_overlay_request()->set_overlay_path(".");
  proto::OverlayFile* added =
      request.mutable_overlay_request()->add_files_to_write();
  added->set_path("apk/hello.txt");
  added->set_content("hello world");

  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  std::string content;
  EXPECT_TRUE(deploy::ReadFile(".overlay/apk/hello.txt", &content));
  EXPECT_EQ("hello world", content);

  fake_exec.JoinServerThread();

  // Start the server and overwrite and delete a file
  client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  request.mutable_overlay_request()->set_expected_overlay_id("id");
  request.mutable_overlay_request()->set_overlay_id("next-id");
  request.mutable_overlay_request()->clear_files_to_write();
  added = request.mutable_overlay_request()->add_files_to_write();
  added->set_path("apk/hello_2.txt");
  added->set_content("hello again world");
  request.mutable_overlay_request()->add_files_to_delete("apk/hello.txt");
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->Read(&response));
  EXPECT_EQ(proto::InstallServerResponse::REQUEST_COMPLETED, response.status());
  EXPECT_EQ(proto::OverlayUpdateResponse::OK,
            response.overlay_response().status());
  EXPECT_TRUE(client->KillServerAndWait(&response));

  content.clear();
  EXPECT_FALSE(deploy::ReadFile(".overlay/apk/hello.txt", &content));
  EXPECT_TRUE(content.empty());

  content.clear();
  EXPECT_TRUE(deploy::ReadFile(".overlay/apk/hello_2.txt", &content));
  EXPECT_EQ("hello again world", content);

  fake_exec.JoinServerThread();
}

TEST_F(InstallServerTest, TestNeedCopy) {
  std::thread server_thread;
  // Run fails, copy succeeds, run succeeds.
  std::deque<bool> success = {false, true, true};
  FakeExecutor fake_exec(server_thread, success);

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->KillServerAndWait(&response));

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestNeedMkdir) {
  std::thread server_thread;
  // Run fails, copy fails, mkdir succeeds, copy succeeds, run succeeds.
  std::deque<bool> success = {false, false, true, true, true};
  FakeExecutor fake_exec(server_thread, success);

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->KillServerAndWait(&response));

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestCopyAndMkdirFails) {
  std::thread server_thread;
  // Run fails, copy fails, mkdir fails, copy fails.
  std::deque<bool> success = {false, false, false, false};
  FakeExecutor fake_exec(server_thread, success);

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  EXPECT_TRUE(nullptr == client);

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
};

// The odd case that the first copy fails, but the directory already exists, and
// the second copy is ok.
TEST_F(InstallServerTest, TestMkdirFailCopySucceedss) {
  std::thread server_thread;
  // Run fails, copy fails, mkdir fails, copy succeeds, run succeeds.
  std::deque<bool> success = {false, false, false, true, true};
  FakeExecutor fake_exec(server_thread, success);

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  ASSERT_FALSE(nullptr == client);

  proto::InstallServerRequest request;
  proto::InstallServerResponse response;
  EXPECT_TRUE(client->Write(request));

  EXPECT_TRUE(client->KillServerAndWait(&response));

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
  fake_exec.JoinServerThread();
};

TEST_F(InstallServerTest, TestAllStartsFail) {
  std::thread server_thread;
  // Run fails, copy succeeds, run fails.
  std::deque<bool> success = {false, true, false};
  FakeExecutor fake_exec(server_thread, success);

  auto client = StartInstallServer(fake_exec, "fakepath", "fakepackage", "iwi");
  EXPECT_TRUE(nullptr == client);

  // Make sure we consumed all the exec results.
  EXPECT_TRUE(success.empty());
};

}  // namespace deploy
