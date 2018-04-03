/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "perfd/sessions/session_utils.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/clock.h"
#include "utils/fake_clock.h"

using profiler::FakeClock;
using profiler::SessionsManager;
using profiler::SessionUtils;
using profiler::proto::Session;

TEST(SessionsManager, CanBeginAndEndASession) {
  FakeClock clock(1234);
  SessionsManager sessions(&clock);

  Session session;
  bool result = sessions.BeginSession(-1, 1, &session, clock.GetCurrentTime());
  EXPECT_TRUE(result);
  clock.Elapse(10);

  EXPECT_EQ(session.start_timestamp(), 1234);
  EXPECT_EQ(session.end_timestamp(), LONG_MAX);
  EXPECT_EQ(session.device_id(), -1);
  EXPECT_EQ(session.pid(), 1);
  EXPECT_TRUE(SessionUtils::IsActive(session));

  Session ended_session;
  result = sessions.EndSession(session.session_id(), &ended_session);
  EXPECT_TRUE(result);
  EXPECT_EQ(session.session_id(), ended_session.session_id());
  EXPECT_EQ(ended_session.end_timestamp(), 1234 + 10);
  EXPECT_FALSE(SessionUtils::IsActive(ended_session));
}

TEST(SessionsManager, CanBeginMultipleSessions_AllRemainActiveUntilEnded) {
  FakeClock clock(1234);
  SessionsManager sessions(&clock);
  Session session1;
  Session session2;
  Session session3;

  sessions.BeginSession(-1, 1, &session1, clock.GetCurrentTime());
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session1));
  EXPECT_EQ(1, session1.pid());

  sessions.BeginSession(-2, 2, &session2, clock.GetCurrentTime());
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session2));
  EXPECT_EQ(2, session2.pid());

  sessions.BeginSession(-3, 3, &session3, clock.GetCurrentTime());
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(session3));
  EXPECT_EQ(3, session3.pid());

  {
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(3, session_range.size());
    EXPECT_TRUE(SessionUtils::IsActive(session_range[0]));
    EXPECT_TRUE(SessionUtils::IsActive(session_range[1]));
    EXPECT_TRUE(SessionUtils::IsActive(session_range[2]));
  }

  // End sessions out of order
  Session ended_session1;
  Session ended_session2;
  Session ended_session3;

  sessions.EndSession(session2.session_id(), &ended_session2);
  EXPECT_FALSE(SessionUtils::IsActive(ended_session1));

  sessions.EndSession(session3.session_id(), &ended_session3);
  EXPECT_FALSE(SessionUtils::IsActive(ended_session3));

  sessions.EndSession(session1.session_id(), &ended_session1);
  EXPECT_FALSE(SessionUtils::IsActive(ended_session1));

  EXPECT_EQ(session1.session_id(), ended_session1.session_id());
  EXPECT_EQ(session2.session_id(), ended_session2.session_id());
  EXPECT_EQ(session3.session_id(), ended_session3.session_id());

  {
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(3, session_range.size());
    EXPECT_FALSE(SessionUtils::IsActive(session_range[0]));
    EXPECT_FALSE(SessionUtils::IsActive(session_range[1]));
    EXPECT_FALSE(SessionUtils::IsActive(session_range[2]));
  }
}

TEST(SessionsManager, GetSessionWorks) {
  FakeClock clock(1000);
  SessionsManager sessions(&clock);

  Session session;
  sessions.BeginSession(-1, 1, &session, clock.GetCurrentTime());
  clock.Elapse(500);
  sessions.EndSession(session.session_id(), &session);

  Session retrieved_session;
  EXPECT_TRUE(sessions.GetSession(session.session_id(), &retrieved_session));
  EXPECT_EQ(session.session_id(), retrieved_session.session_id());

  // Getting a non-existent session returns false.
  EXPECT_FALSE(
      sessions.GetSession(session.session_id() - 1, &retrieved_session));
}

TEST(SessionsManager, GetActiveSessionByPid) {
  FakeClock clock(1000);
  SessionsManager sessions(&clock);

  Session session;
  sessions.BeginSession(-1, 1, &session, clock.GetCurrentTime());
  clock.Elapse(500);

  Session retrieved_session;
  EXPECT_TRUE(
      sessions.GetActiveSessionByPid(session.pid(), &retrieved_session));
  EXPECT_EQ(session.session_id(), retrieved_session.session_id());
  EXPECT_EQ(session.pid(), retrieved_session.pid());

  // Getting an inactive sessin should return false.
  sessions.EndSession(session.session_id(), &session);
  EXPECT_FALSE(
      sessions.GetActiveSessionByPid(session.pid(), &retrieved_session));
}

TEST(SessionsManager, GetSessionsByTimeRangeWorks) {
  FakeClock clock(1000);
  SessionsManager sessions(&clock);
  Session session;

  // Session from 1000 to 1500.
  sessions.BeginSession(-10, 10, &session, clock.GetCurrentTime());
  clock.Elapse(500);
  sessions.EndSession(session.session_id(), &session);

  // Session from 2000 to 2500.
  clock.Elapse(500);
  sessions.BeginSession(-20, 20, &session, clock.GetCurrentTime());
  clock.Elapse(500);
  sessions.EndSession(session.session_id(), &session);

  // Session from 3000 to 3500.
  clock.Elapse(500);
  sessions.BeginSession(-30, 30, &session, clock.GetCurrentTime());
  clock.Elapse(500);
  sessions.EndSession(session.session_id(), &session);

  // Session from 4000 to 4500.
  clock.Elapse(500);
  sessions.BeginSession(-40, 40, &session, clock.GetCurrentTime());
  clock.Elapse(500);
  sessions.EndSession(session.session_id(), &session);

  // Session from 5000 to present.
  clock.Elapse(500);
  sessions.BeginSession(-50, 50, &session, clock.GetCurrentTime());
  EXPECT_TRUE(SessionUtils::IsActive(session));

  {
    // Get all
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(5, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(50, session_range[0].pid());
    EXPECT_EQ(40, session_range[1].pid());
    EXPECT_EQ(30, session_range[2].pid());
    EXPECT_EQ(20, session_range[3].pid());
    EXPECT_EQ(10, session_range[4].pid());
  }

  {
    // Get all sessions ended after a time range
    auto session_range = sessions.GetSessions(3250);
    EXPECT_EQ(3, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(50, session_range[0].pid());
    EXPECT_EQ(40, session_range[1].pid());
    EXPECT_EQ(30, session_range[2].pid());
  }

  {
    // Get all sessions started before a time range
    auto session_range = sessions.GetSessions(0, 3250);
    EXPECT_EQ(3, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(30, session_range[0].pid());
    EXPECT_EQ(20, session_range[1].pid());
    EXPECT_EQ(10, session_range[2].pid());
  }

  {
    // Get sessions between two time ranges
    auto session_range = sessions.GetSessions(2250, 3250);
    EXPECT_EQ(2, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(30, session_range[0].pid());
    EXPECT_EQ(20, session_range[1].pid());
  }

  {
    // An active session has no end timestamp and, until it ends, is assumed to
    // extend forever.
    auto session_range = sessions.GetSessions(clock.GetCurrentTime() + 1000);
    EXPECT_EQ(1, session_range.size());
    EXPECT_EQ(50, session_range[0].pid());
  }
}

TEST(SessionsManager, CallingBeginSessionOnActiveSessionSortsItToFront) {
  FakeClock clock(1000);
  SessionsManager sessions(&clock);
  Session session1;
  Session session2;
  Session session3;
  Session session4;

  sessions.BeginSession(-10, 10, &session1, clock.GetCurrentTime());
  sessions.BeginSession(-20, 20, &session2, clock.GetCurrentTime());
  sessions.BeginSession(-30, 30, &session3, clock.GetCurrentTime());
  sessions.BeginSession(-40, 40, &session4, clock.GetCurrentTime());

  {
    // Sanity check
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(4, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(40, session_range[0].pid());
    EXPECT_EQ(30, session_range[1].pid());
    EXPECT_EQ(20, session_range[2].pid());
    EXPECT_EQ(10, session_range[3].pid());
  }

  Session session2_copy;
  // Beginning an existent session returns false.
  EXPECT_FALSE(sessions.BeginSession(-20, 20, &session2_copy, clock.GetCurrentTime()));
  EXPECT_EQ(session2.pid(), session2_copy.pid());

  {
    // Session2 promoted to most recent
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(4, session_range.size());

    // Range is returned most recent first.
    EXPECT_EQ(20, session_range[0].pid());
    EXPECT_EQ(40, session_range[1].pid());
    EXPECT_EQ(30, session_range[2].pid());
    EXPECT_EQ(10, session_range[3].pid());
  }

  // Session #2 is already the most recent. Calling |BeginSession| again is a
  // no-op.
  EXPECT_FALSE(sessions.BeginSession(-20, 20, &session2_copy, clock.GetCurrentTime()));
  {
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(20, session_range[0].pid());
  }
}

TEST(SessionsManager,
     CallingBeginEndBeginWithSameParametersWillCreateNewSession) {
  FakeClock clock(1000);
  SessionsManager sessions(&clock);
  Session session;

  EXPECT_EQ(0, sessions.GetSessions().size());
  sessions.BeginSession(-10, 10, &session, clock.GetCurrentTime());
  EXPECT_EQ(1, sessions.GetSessions().size());
  sessions.BeginSession(-10, 10, &session, clock.GetCurrentTime());
  EXPECT_EQ(1, sessions.GetSessions().size());
  sessions.EndSession(session.session_id(), &session);
  sessions.BeginSession(-10, 10, &session, clock.GetCurrentTime());

  {
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(2, session_range.size());
    EXPECT_EQ(10, session_range[0].pid());
    EXPECT_TRUE(SessionUtils::IsActive(session_range[0]));
    // Same PID, etc., but this session is dead
    EXPECT_EQ(10, session_range[1].pid());
    EXPECT_FALSE(SessionUtils::IsActive(session_range[1]));
  }
}

TEST(SessionsManager, UniqueSessionIds) {
  FakeClock clock(1234);
  SessionsManager sessions(&clock);

  std::unordered_set<int64_t> session_ids;
  for (int32_t device_id = 0; device_id < 100; device_id++) {
    for (int64_t start_time = 0; start_time < 10000; start_time += 100) {
      clock.SetCurrentTime(start_time);
      Session session;
      bool result = sessions.BeginSession(device_id, start_time, &session, clock.GetCurrentTime());
      EXPECT_TRUE(result);
      EXPECT_EQ(session_ids.end(), session_ids.find(session.session_id()));
      session_ids.insert(session.session_id());
    }
  }
}