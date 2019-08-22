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
#include <gtest/gtest.h>
#include <climits>

#include <grpc++/grpc++.h>
#include "daemon/agent_service.h"
#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "proto/common.grpc.pb.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/log.h"

using profiler::proto::EmptyResponse;
using profiler::proto::SendBytesRequest;
using std::unique_ptr;

namespace profiler {

class AgentServiceTest : public ::testing::Test {
 public:
  AgentServiceTest()
      :  // Due to the test depends on the implementation of
         // FileSystem::HasFile(), we are using DiskFileSystem, not
         // MemoryFileSystem.
        file_cache_(getenv("TEST_TMPDIR")),
        config_(proto::DaemonConfig::default_instance()),
        buffer_(&clock_, 10, 5),
        daemon_(&clock_, &config_, &file_cache_, &buffer_),
        service_(&daemon_) {}

  void SetUp() override {
    grpc::ServerBuilder builder;
    int port;
    builder.AddListeningPort("0.0.0.0:0", grpc::InsecureServerCredentials(),
                             &port);
    builder.RegisterService(&service_);
    server_ = builder.BuildAndStart();
    std::shared_ptr<grpc::ChannelInterface> channel =
        grpc::CreateChannel(std::string("0.0.0.0:") + std::to_string(port),
                            grpc::InsecureChannelCredentials());
    stub_ = proto::AgentService::NewStub(channel);
  }

  void TearDown() override {
    daemon_.InterruptWriteEvents();
    server_->Shutdown();
  }

  void SendBytesHelper(const SendBytesRequest& request) {
    grpc::ClientContext context;
    EmptyResponse response;
    stub_->SendBytes(&context, request, &response);
  }

  FakeClock clock_;
  FileCache file_cache_;
  DaemonConfig config_;
  EventBuffer buffer_;
  Daemon daemon_;
  AgentServiceImpl service_;

  unique_ptr<::grpc::Server> server_;
  unique_ptr<proto::AgentService::Stub> stub_;
};

TEST_F(AgentServiceTest, FileExistsOnlyAfterBeingMarkedComplete) {
  std::string file_name{"FakeFileName"};
  std::string chunk{"0123456789"};  // 10 bytes long

  SendBytesRequest request;
  request.set_name(file_name);
  request.set_bytes(chunk);
  SendBytesHelper(request);
  // The file shouldn't exist before being marked as 'complete', even if bytes
  // have been added to it.
  EXPECT_FALSE(file_cache_.GetFile(file_name)->Exists());

  // File shouldn't exist before being marked as 'complete'.
  request.set_is_complete(true);
  SendBytesHelper(request);
  EXPECT_TRUE(file_cache_.GetFile(file_name)->Exists());
  // |complete| and |bytes| are in the oneof union. Setting one clears the
  // other.
  EXPECT_EQ(chunk, file_cache_.GetFile(file_name)->Contents());
}

TEST_F(AgentServiceTest, CanAddMultipleChunks) {
  std::string file_name{"FakeFileName"};
  std::string chunk{"0123456789"};  // 10 bytes long
  int number_of_chunks = 7;

  SendBytesRequest request;
  request.set_name(file_name);
  request.set_bytes(chunk);
  for (int i = 0; i < number_of_chunks; i++) {
    SendBytesHelper(request);
  }
  request.set_is_complete(true);
  SendBytesHelper(request);
  EXPECT_EQ(chunk.size() * number_of_chunks,
            file_cache_.GetFile(file_name)->Contents().size());
}

TEST_F(AgentServiceTest, CanMarkCompleteMultipleTimes) {
  std::string file_name{"FakeFileName"};
  std::string chunk{"0123456789"};  // 10 bytes long

  SendBytesRequest request;
  request.set_name(file_name);
  request.set_bytes(chunk);
  SendBytesHelper(request);
  request.set_is_complete(true);
  SendBytesHelper(request);
  EXPECT_EQ(chunk, file_cache_.GetFile(file_name)->Contents());
  // Send another 'complete' request.
  SendBytesHelper(request);
  EXPECT_EQ(chunk, file_cache_.GetFile(file_name)->Contents());
}

TEST_F(AgentServiceTest, MustAddBytesToCreateAFile) {
  std::string file_name{"FakeFileName"};
  std::string chunk{"0123456789"};  // 10 bytes long

  EXPECT_FALSE(file_cache_.GetFile(file_name)->Exists());
  SendBytesRequest request;
  request.set_name(file_name);
  request.set_is_complete(true);
  SendBytesHelper(request);
  // The file shouldn't exist if there's no bytes ever added to it, even if it's
  // marked 'complete'.
  EXPECT_FALSE(file_cache_.GetFile(file_name)->Exists());
}

TEST_F(AgentServiceTest, AddZeroBytesToCreateEmptyFile) {
  std::string file_name{"FakeFileName"};

  SendBytesRequest request;
  request.set_name(file_name);
  request.set_bytes("");
  SendBytesHelper(request);
  request.set_is_complete(true);
  SendBytesHelper(request);
  EXPECT_TRUE(file_cache_.GetFile(file_name)->Exists());
  EXPECT_EQ("", file_cache_.GetFile(file_name)->Contents());
}

}  // namespace profiler