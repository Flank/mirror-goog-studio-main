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
#include <fstream>

#include <grpc++/grpc++.h>
#include "daemon/agent_service.h"
#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "proto/common.grpc.pb.h"
#include "utils/agent_task.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/file_reader.h"
#include "utils/log.h"

using profiler::proto::EmptyResponse;
using profiler::proto::SendBytesRequest;
using std::string;
using std::unique_ptr;

namespace {

// Creates a file of the given name and give size in KB.
// The content are printable ASCII characters repeating
// "abcdefg...xyz", for easy testing.
void CreateTestFile(const string& file_name, int size_in_kb) {
  // Create a buffer of 1 KB.
  const int kBufferSize = 1024;  // One KB
  char buffer[kBufferSize + 1];
  for (int i = 0; i < kBufferSize; i++) {
    buffer[i] = (char)(i % 26 + 'a');
  }
  buffer[kBufferSize] = '\0';
  // Repeatedly write the buffer into the file.
  std::ofstream output_file{file_name};
  for (int i = 0; i < size_in_kb; i++) {
    output_file << buffer;
  }
  output_file.close();
}

}  // namespace

namespace profiler {

class AgentTaskTest : public ::testing::Test {
 public:
  AgentTaskTest()
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
    // Perfd::Initialize(&daemon_);
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

  FakeClock clock_;
  FileCache file_cache_;
  DaemonConfig config_;
  EventBuffer buffer_;
  Daemon daemon_;
  AgentServiceImpl service_;

  unique_ptr<::grpc::Server> server_;
  unique_ptr<proto::AgentService::Stub> stub_;
};

TEST_F(AgentTaskTest, TestCreateTasksToSendPayload) {
  std::string file_name{"FakeFileName"};
  const int kFileSizeInKb = 8 * 1024;  // Assume a 8 MB file.
  const int kFileSizeInByte = kFileSizeInKb * 1024;
  CreateTestFile(file_name, kFileSizeInKb);

  std::string file_content;
  profiler::FileReader::Read(file_name, &file_content);
  EXPECT_EQ(kFileSizeInByte, file_content.size());

  const auto& tasks = CreateTasksToSendPayload(file_name, file_content, true);
  // 4 tasks required in total.
  // 4,000,000 bytes per task, 8 MB needs 3 tasks; plus 1 for mark it complete.
  EXPECT_EQ(4, tasks.size());
  for (auto task : tasks) {
    // Each grpc call needs a new ClientContext.
    grpc::ClientContext ctx;
    task(*(stub_.get()), ctx);
  }

  ASSERT_EQ(kFileSizeInByte, file_cache_.GetFile(file_name)->Contents().size());
  EXPECT_EQ(file_content, file_cache_.GetFile(file_name)->Contents());
}

}  // namespace profiler