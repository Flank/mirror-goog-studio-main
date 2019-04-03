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
#include <unordered_set>

#include <grpc++/grpc++.h>
#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "daemon/transport_service.h"
#include "perfd/perfd.h"
#include "proto/common.grpc.pb.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"
#include "utils/log.h"

using std::unique_ptr;

namespace profiler {

class TransportServiceTest : public ::testing::Test {
 public:
  TransportServiceTest()
      : file_cache_(unique_ptr<FileSystem>(new MemoryFileSystem()), "/"),
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
    Perfd::Initialize(&daemon_);
    server_ = builder.BuildAndStart();
    std::shared_ptr<grpc::ChannelInterface> channel =
        grpc::CreateChannel(std::string("0.0.0.0:") + std::to_string(port),
                            grpc::InsecureChannelCredentials());
    stub_ = proto::TransportService::NewStub(channel);
    // Create a new thread to read events on.
    events_thread_ = std::thread(&TransportServiceTest::GetEvents, this);
  }

  void GetEvents() {
    proto::GetEventsRequest request;
    events_reader_ = stub_->GetEvents(&events_context_, request);
    proto::Event event;
    // Read is a blocking call.
    while (events_reader_->Read(&event)) {
      std::unique_lock<std::mutex> lock(events_mutex_);
      events_.push(event);
      events_cv_.notify_all();
    }
  }

  std::unique_ptr<proto::Event> PopEvent() {
    std::unique_lock<std::mutex> lock(events_mutex_);
    // Check events list for elements, if no elements found block until
    // we timeout (returning null) or we have an event.
    if (events_cv_.wait_for(lock, std::chrono::milliseconds(500),
                            [this] { return !events_.empty(); })) {
      proto::Event event = events_.front();
      events_.pop();
      return std::unique_ptr<proto::Event>(new proto::Event(event));
    }
    return nullptr;
  }

  void TearDown() override {
    // Stop client and server listeners.
    daemon_.InterruptWriteEvents();
    events_context_.TryCancel();
    events_reader_->Finish();
    events_thread_.join();
    server_->Shutdown();
  }

  FakeClock clock_;
  FileCache file_cache_;
  DaemonConfig config_;
  EventBuffer buffer_;
  Daemon daemon_;
  TransportServiceImpl service_;
  std::mutex events_mutex_;
  std::condition_variable events_cv_;
  std::queue<proto::Event> events_;
  std::thread events_thread_;
  grpc::ClientContext events_context_;

  std::unique_ptr<::grpc::Server> server_;
  std::unique_ptr<proto::TransportService::Stub> stub_;
  std::unique_ptr<grpc::ClientReader<proto::Event>> events_reader_;
};

TEST_F(TransportServiceTest, TestBeginSessionCommand) {
  proto::ExecuteRequest req;
  proto::Command* command = req.mutable_command();
  command->set_stream_id(100);
  command->set_type(proto::Command::BEGIN_SESSION);
  command->set_pid(1000);
  command->mutable_begin_session();

  proto::ExecuteResponse res;
  clock_.SetCurrentTime(2);
  grpc::ClientContext context;
  stub_->Execute(&context, req, &res);
  // Validate command created session started event.
  std::unique_ptr<proto::Event> event = PopEvent();
  EXPECT_EQ(2, event->timestamp());
  EXPECT_EQ(proto::Event::SESSION, event->kind());
  EXPECT_FALSE(event->is_ended());
  EXPECT_TRUE(event->has_session());
  EXPECT_TRUE(event->session().has_session_started());

  clock_.SetCurrentTime(4);
  grpc::ClientContext context2;
  command->set_stream_id(100);
  command->set_pid(1001);
  stub_->Execute(&context2, req, &res);
  // Validate when a new session is started a previous session is ended.
  event = PopEvent();
  EXPECT_EQ(4, event->timestamp());
  EXPECT_EQ(proto::Event::SESSION, event->kind());
  EXPECT_TRUE(event->is_ended());
  event = PopEvent();
  EXPECT_EQ(4, event->timestamp());
  EXPECT_EQ(proto::Event::SESSION, event->kind());
  EXPECT_FALSE(event->is_ended());
  EXPECT_TRUE(event->has_session());
  EXPECT_TRUE(event->session().has_session_started());
}
}  // namespace profiler