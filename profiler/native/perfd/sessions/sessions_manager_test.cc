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
  SessionsManager sessions(clock);

  auto *session1 = sessions.BeginSession("FakeSerial1", "FakeBoot1", 1);
  clock.Elapse(10);

  EXPECT_EQ(session1->start_timestamp(), 1234);
  EXPECT_EQ(session1->end_timestamp(), LONG_MAX);
  EXPECT_EQ(session1->device_serial(), "FakeSerial1");
  EXPECT_EQ(session1->boot_id(), "FakeBoot1");
  EXPECT_EQ(session1->pid(), 1);
  EXPECT_TRUE(SessionUtils::IsActive(*session1));

  sessions.EndSession(session1->session_id());
  EXPECT_EQ(session1->end_timestamp(), 1234 + 10);
  EXPECT_FALSE(SessionUtils::IsActive(*session1));
}

TEST(SessionsManager, CanBeginMultipleSessions_AllRemainActiveUntilEnded) {
  FakeClock clock(1234);
  SessionsManager sessions(clock);

  auto *session1 = sessions.BeginSession("FakeSerial1", "FakeBoot1", 1);
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(*session1));
  EXPECT_EQ(1, session1->pid());

  auto *session2 = sessions.BeginSession("FakeSerial2", "FakeBoot2", 2);
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(*session1));
  EXPECT_TRUE(SessionUtils::IsActive(*session2));
  EXPECT_EQ(2, session2->pid());

  auto *session3 = sessions.BeginSession("FakeSerial3", "FakeBoot3", 3);
  clock.Elapse(10);
  EXPECT_TRUE(SessionUtils::IsActive(*session1));
  EXPECT_TRUE(SessionUtils::IsActive(*session2));
  EXPECT_TRUE(SessionUtils::IsActive(*session3));
  EXPECT_EQ(3, session3->pid());

  // Make sure there aren't any stale pointer issues
  EXPECT_EQ(1, session1->pid());
  EXPECT_EQ("FakeSerial1", session1->device_serial());
  EXPECT_EQ(2, session2->pid());
  EXPECT_EQ("FakeSerial2", session2->device_serial());

  // End sessions out of order

  sessions.EndSession(session2->session_id());
  EXPECT_TRUE(SessionUtils::IsActive(*session1));
  EXPECT_FALSE(SessionUtils::IsActive(*session2));
  EXPECT_TRUE(SessionUtils::IsActive(*session3));

  sessions.EndSession(session3->session_id());
  EXPECT_TRUE(SessionUtils::IsActive(*session1));
  EXPECT_FALSE(SessionUtils::IsActive(*session2));
  EXPECT_FALSE(SessionUtils::IsActive(*session3));

  sessions.EndSession(session1->session_id());
  EXPECT_FALSE(SessionUtils::IsActive(*session1));
  EXPECT_FALSE(SessionUtils::IsActive(*session2));
  EXPECT_FALSE(SessionUtils::IsActive(*session3));
}

TEST(SessionsManager, CanDeleteSessions) {
  FakeClock clock(1234);
  SessionsManager sessions(clock);

  auto *session1 = sessions.BeginSession("FakeSerial1", "FakeBoot1", 1);
  clock.Elapse(10);
  auto *session2 = sessions.BeginSession("FakeSerial2", "FakeBoot2", 2);
  clock.Elapse(10);
  auto *session3 = sessions.BeginSession("FakeSerial3", "FakeBoot3", 3);
  clock.Elapse(10);
  auto *session4 = sessions.BeginSession("FakeSerial4", "FakeBoot4", 3);
  clock.Elapse(10);

  // Can delete most recent. (This deactivates the session, if active)
  EXPECT_TRUE(SessionUtils::IsActive(*session4));
  sessions.DeleteSession(session4->session_id());
  EXPECT_FALSE(SessionUtils::IsActive(*session4));

  // Can delete in the middle.
  sessions.DeleteSession(session2->session_id());
  EXPECT_FALSE(SessionUtils::IsActive(*session2));

  // Make sure there aren't any stale pointer issues
  EXPECT_EQ(1, session1->pid());
  EXPECT_EQ("FakeSerial1", session1->device_serial());
  EXPECT_EQ(3, session3->pid());
  EXPECT_EQ("FakeSerial3", session3->device_serial());
}

TEST(SessionsManager, GetSessionWorks) {
  FakeClock clock(1000);
  SessionsManager sessions(clock);

  auto *session = sessions.BeginSession("FakeSerial1", "FakeBoot1", 1);
  clock.Elapse(500);
  sessions.EndSession(session->session_id());

  EXPECT_EQ(1, sessions.GetSession(session->session_id())->pid());
  EXPECT_EQ(nullptr, sessions.GetSession(session->session_id() - 1));
}

TEST(SessionsManager, GetSessionsByTimeRangeWorks) {
  FakeClock clock(1000);
  SessionsManager sessions(clock);

  auto *session =
      sessions.BeginSession("FakeSerial1000to1500", "FakeBoot1", 10);
  clock.Elapse(500);
  sessions.EndSession(session->session_id());

  clock.Elapse(500);
  session = sessions.BeginSession("FakeSerial2000to2500", "FakeBoot2", 20);
  clock.Elapse(500);
  sessions.EndSession(session->session_id());

  clock.Elapse(500);
  session = sessions.BeginSession("FakeSerial3000to3500", "FakeBoot3", 30);
  clock.Elapse(500);
  sessions.EndSession(session->session_id());

  clock.Elapse(500);
  session = sessions.BeginSession("FakeSerial4000to4500", "FakeBoot4", 40);
  clock.Elapse(500);
  sessions.EndSession(session->session_id());

  clock.Elapse(500);
  session = sessions.BeginSession("FakeSerial5000active", "FakeBoot5", 50);
  EXPECT_TRUE(SessionUtils::IsActive(*session));

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

  {
    // Delete most recent session, and check that it is excluded from a search
    sessions.DeleteSession(session->session_id());

    auto session_range = sessions.GetSessions();
    EXPECT_EQ(4, session_range.size());
    EXPECT_EQ(40, session_range[0].pid());
  }
}

TEST(SessionsManager, CallingBeginSessionOnActiveSessionSortsItToFront) {
  FakeClock clock(1000);
  SessionsManager sessions(clock);

  sessions.BeginSession("FakeSerial1", "FakeBoot1", 10);
  auto *session2 = sessions.BeginSession("FakeSerial2", "FakeBoot2", 20);
  sessions.BeginSession("FakeSerial3", "FakeBoot3", 30);
  sessions.BeginSession("FakeSerial4", "FakeBoot4", 40);

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

  auto *session2_copy = sessions.BeginSession("FakeSerial2", "FakeBoot2", 20);
  EXPECT_EQ(session2, session2_copy);

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
  sessions.BeginSession("FakeSerial2", "FakeBoot2", 20);
  {
    auto session_range = sessions.GetSessions();
    EXPECT_EQ(20, session_range[0].pid());
  }
}

TEST(SessionsManager,
     CallingBeginEndBeginWithSameParametersWillCreateNewSession) {
  FakeClock clock(1000);
  SessionsManager sessions(clock);

  EXPECT_EQ(0, sessions.GetSessions().size());
  auto session1 = sessions.BeginSession("FakeSerial1", "FakeBoot1", 10);
  EXPECT_EQ(1, sessions.GetSessions().size());
  sessions.BeginSession("FakeSerial1", "FakeBoot1", 10);
  EXPECT_EQ(1, sessions.GetSessions().size());
  sessions.EndSession(session1->session_id());
  sessions.BeginSession("FakeSerial1", "FakeBoot1", 10);

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
