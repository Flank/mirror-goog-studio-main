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
#include <climits>
#include <unordered_set>

#include <grpc++/grpc++.h>
#include "perfd/profiler_service.h"
#include "perfd/sessions/session_utils.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

using std::unique_ptr;

namespace profiler {

class ProfilerServiceTest : public ::testing::Test {
 public:
  ProfilerServiceTest()
      : file_cache_(unique_ptr<FileSystem>(new MemoryFileSystem()), "/"),
        config_(agent_config_),
        daemon_(&clock_, &config_, &file_cache_),
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
    stub_ = proto::ProfilerService::NewStub(channel);
  }

  proto::Session BeginSession(int32_t device_id, int32_t pid) {
    proto::BeginSessionRequest req;
    req.set_device_id(device_id);
    req.set_pid(pid);
    grpc::ClientContext context;
    proto::BeginSessionResponse res;
    stub_->BeginSession(&context, req, &res);
    return res.session();
  }

  proto::Session EndSession(int32_t device_id, int32_t session_id) {
    proto::EndSessionRequest req;
    req.set_device_id(device_id);
    req.set_session_id(session_id);
    grpc::ClientContext context;
    proto::EndSessionResponse res;
    stub_->EndSession(&context, req, &res);
    return res.session();
  }

  proto::Session GetSession(int32_t session_id) {
    proto::GetSessionRequest req;
    req.set_session_id(session_id);
    grpc::ClientContext context;
    proto::GetSessionResponse res;
    stub_->GetSession(&context, req, &res);
    return res.session();
  }

  proto::GetSessionsResponse GetSessions(int64_t start, int64_t end) {
    proto::GetSessionsRequest request;
    request.set_start_timestamp(start);
    request.set_end_timestamp(end);
    grpc::ClientContext context;
    proto::GetSessionsResponse sessions;
    stub_->GetSessions(&context, request, &sessions);
    return sessions;
  }

  void TearDown() { server_->Shutdown(); }

  FakeClock clock_;
  FileCache file_cache_;
  proto::AgentConfig agent_config_;
  Config config_;
  Daemon daemon_;
  ProfilerServiceImpl service_;

  std::unique_ptr<::grpc::Server> server_;
  std::unique_ptr<proto::ProfilerService::Stub> stub_;
};

TEST_F(ProfilerServiceTest, TestBeginSession) {
  clock_.SetCurrentTime(2);
  BeginSession(100, 1000);
  clock_.SetCurrentTime(4);
  BeginSession(101, 1001);
  proto::GetSessionsResponse sessions = GetSessions(1, 3);
  ASSERT_EQ(1, sessions.sessions_size());
  sessions = GetSessions(1, 5);
  ASSERT_EQ(2, sessions.sessions_size());
  sessions = GetSessions(3, 5);
  ASSERT_EQ(2, sessions.sessions_size());
}

TEST_F(ProfilerServiceTest, CanBeginAndEndASession) {
  clock_.SetCurrentTime(1234);
  proto::Session begin = BeginSession(2222, 1);

  EXPECT_EQ(begin.start_timestamp(), 1234);
  EXPECT_EQ(begin.end_timestamp(), LONG_MAX);
  EXPECT_EQ(begin.device_id(), 2222);
  EXPECT_EQ(begin.pid(), 1);
  EXPECT_TRUE(SessionUtils::IsActive(begin));

  clock_.Elapse(10);
  // Session ended_session;
  proto::Session end = EndSession(2222, begin.session_id());
  EXPECT_EQ(begin.session_id(), end.session_id());
  // Test ids to ensure they are stable
  EXPECT_EQ(0x10A, end.session_id());
  EXPECT_EQ(end.end_timestamp(), 1234 + 10);
  EXPECT_FALSE(SessionUtils::IsActive(end));
}

TEST_F(ProfilerServiceTest, BeginClosesPreviousSession) {
  clock_.SetCurrentTime(1234);
  proto::Session session1 = BeginSession(-1, 1);
  clock_.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session1));
  EXPECT_EQ(1, session1.pid());

  proto::Session session2 = BeginSession(-2, 2);
  clock_.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session2));
  EXPECT_EQ(2, session2.pid());

  proto::Session session3 = BeginSession(-3, 3);
  clock_.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session3));
  EXPECT_EQ(3, session3.pid());
  {
    proto::GetSessionsResponse sessions = GetSessions(LLONG_MIN, LLONG_MAX);
    EXPECT_EQ(3, sessions.sessions_size());
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(0)));
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(1)));
    EXPECT_TRUE(SessionUtils::IsActive(sessions.sessions(2)));
  }

  // End and already ended session
  EndSession(-1, session2.session_id());
  {
    proto::GetSessionsResponse sessions = GetSessions(LLONG_MIN, LLONG_MAX);
    EXPECT_EQ(3, sessions.sessions_size());
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(0)));
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(1)));
    EXPECT_TRUE(SessionUtils::IsActive(sessions.sessions(2)));
  }

  // End the last session
  EndSession(-1, session3.session_id());
  {
    proto::GetSessionsResponse sessions = GetSessions(LLONG_MIN, LLONG_MAX);
    EXPECT_EQ(3, sessions.sessions_size());
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(0)));
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(1)));
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(2)));
  }
}

TEST_F(ProfilerServiceTest, GetSessionWorks) {
  clock_.SetCurrentTime(1000);

  proto::Session session = BeginSession(-1, 1);
  clock_.Elapse(500);
  session = EndSession(-1, session.session_id());

  proto::Session retrieved_session = GetSession(session.session_id());
  EXPECT_EQ(session.session_id(), retrieved_session.session_id());

  // Getting a non-existent session returns an empty session.
  session = GetSession(session.session_id() - 1);
  EXPECT_EQ(0, session.session_id());
}

TEST_F(ProfilerServiceTest, GetSessionsByTimeRangeWorks) {
  clock_.SetCurrentTime(1000);
  // Session from 1000 to 1500.
  proto::Session session = BeginSession(-10, 10);
  clock_.Elapse(500);
  session = EndSession(-1, session.session_id());

  // Session from 2000 to 2500.
  clock_.Elapse(500);
  session = BeginSession(-20, 20);
  clock_.Elapse(500);
  session = EndSession(-1, session.session_id());

  // Session from 3000 to 3500.
  clock_.Elapse(500);
  session = BeginSession(-30, 30);
  clock_.Elapse(500);
  session = EndSession(-1, session.session_id());

  // Session from 4000 to 4500.
  clock_.Elapse(500);
  session = BeginSession(-40, 40);
  clock_.Elapse(500);
  session = EndSession(-1, session.session_id());

  // Session from 5000 to present.
  clock_.Elapse(500);
  session = BeginSession(-50, 50);
  EXPECT_TRUE(SessionUtils::IsActive(session));

  {
    // Get all
    auto sessions = GetSessions(LLONG_MIN, LLONG_MAX);
    EXPECT_EQ(5, sessions.sessions_size());

    EXPECT_EQ(10, sessions.sessions(0).pid());
    EXPECT_EQ(20, sessions.sessions(1).pid());
    EXPECT_EQ(30, sessions.sessions(2).pid());
    EXPECT_EQ(40, sessions.sessions(3).pid());
    EXPECT_EQ(50, sessions.sessions(4).pid());
  }

  {
    // Get all sessions ended after a time range
    auto sessions = GetSessions(3250, LLONG_MAX);
    EXPECT_EQ(3, sessions.sessions_size());

    EXPECT_EQ(30, sessions.sessions(0).pid());
    EXPECT_EQ(40, sessions.sessions(1).pid());
    EXPECT_EQ(50, sessions.sessions(2).pid());
  }

  {
    // Get all sessions started before a time range
    auto sessions = GetSessions(0, 3250);
    EXPECT_EQ(3, sessions.sessions_size());

    EXPECT_EQ(10, sessions.sessions(0).pid());
    EXPECT_EQ(20, sessions.sessions(1).pid());
    EXPECT_EQ(30, sessions.sessions(2).pid());
  }

  {
    // Get sessions between two time ranges
    auto sessions = GetSessions(2250, 3250);
    EXPECT_EQ(2, sessions.sessions_size());

    EXPECT_EQ(20, sessions.sessions(0).pid());
    EXPECT_EQ(30, sessions.sessions(1).pid());
  }

  {
    // An active session has no end timestamp and, until it ends, is assumed
    // extend forever.
    auto sessions = GetSessions(clock_.GetCurrentTime() + 1000, LLONG_MAX);
    EXPECT_EQ(1, sessions.sessions_size());
    EXPECT_EQ(50, sessions.sessions(0).pid());
  }
}

TEST_F(ProfilerServiceTest, BeginingTwiceIsTheSameAsEndingInBetween) {
  clock_.SetCurrentTime(1000);
  proto::Session session;

  EXPECT_EQ(0, GetSessions(LLONG_MIN, LLONG_MAX).sessions_size());
  session = BeginSession(-10, 10);
  EXPECT_EQ(1, GetSessions(LLONG_MIN, LLONG_MAX).sessions_size());
  session = BeginSession(-10, 10);
  EXPECT_EQ(2, GetSessions(LLONG_MIN, LLONG_MAX).sessions_size());
  session = EndSession(-1, session.session_id());
  session = BeginSession(-10, 10);

  {
    auto sessions = GetSessions(LLONG_MIN, LLONG_MAX);
    EXPECT_EQ(3, sessions.sessions_size());
    EXPECT_EQ(10, sessions.sessions(0).pid());
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(0)));
    EXPECT_EQ(10, sessions.sessions(1).pid());
    EXPECT_FALSE(SessionUtils::IsActive(sessions.sessions(1)));
    EXPECT_EQ(10, sessions.sessions(2).pid());
    EXPECT_TRUE(SessionUtils::IsActive(sessions.sessions(2)));
  }
}

TEST_F(ProfilerServiceTest, UniqueSessionIds) {
  clock_.SetCurrentTime(1234);

  std::unordered_set<int64_t> session_ids;
  for (int32_t device_id = 0; device_id < 100; device_id++) {
    for (int64_t start_time = 0; start_time < 10000; start_time += 100) {
      clock_.SetCurrentTime(start_time);
      proto::Session session = BeginSession(device_id, start_time);
      EXPECT_EQ(session_ids.end(), session_ids.find(session.session_id()));
      session_ids.insert(session.session_id());
    }
  }
}

}  // namespace profiler