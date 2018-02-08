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

#include "energy_cache.h"

using profiler::EnergyCache;
using profiler::proto::EnergyEvent;
using profiler::proto::EnergyEventsResponse;

TEST(EnergyCache, IsEmptyInitially) {
  EnergyCache energy_cache;
  auto result = energy_cache.GetEnergyEvents(0, LLONG_MIN, LLONG_MAX);
  EXPECT_EQ(0, result.size());
}

TEST(EnergyCache, AddEnergyEvent) {
  EnergyEvent energy_event;
  energy_event.set_timestamp(1000);
  energy_event.mutable_wake_lock_acquired();
  energy_event.set_pid(1);
  EnergyCache energy_cache;
  energy_cache.AddEnergyEvent(energy_event);

  auto result = energy_cache.GetEnergyEvents(1, LLONG_MIN, LLONG_MAX);
  EXPECT_EQ(1, result.size());
  EXPECT_EQ(1000, result.at(0).timestamp());
  EXPECT_EQ(EnergyEvent::kWakeLockAcquired, result.at(0).metadata_case());
  EXPECT_EQ(1, result.at(0).pid());
}

TEST(EnergyCache, GetEnergyEventsOfAppId) {
  EnergyEvent energy_event_app1, energy_event_app2;
  energy_event_app1.set_pid(1);
  energy_event_app1.set_event_id(1);
  energy_event_app2.set_pid(2);
  energy_event_app2.set_event_id(1);
  EnergyCache energy_cache;
  energy_cache.AddEnergyEvent(energy_event_app1);
  energy_cache.AddEnergyEvent(energy_event_app2);

  auto result = energy_cache.GetEnergyEvents(2, LLONG_MIN, LLONG_MAX);
  EXPECT_EQ(1, result.size());
  EXPECT_EQ(2, result.at(0).pid());
}

TEST(EnergyCache, GetEnergyEventsWithinTimeRange) {
  EnergyEvent energy_event_time1, energy_event_time2;
  energy_event_time1.set_pid(1);
  energy_event_time1.set_timestamp(1000);
  energy_event_time2.set_pid(1);
  energy_event_time2.set_timestamp(2000);
  EnergyCache energy_cache;
  energy_cache.AddEnergyEvent(energy_event_time1);
  energy_cache.AddEnergyEvent(energy_event_time2);

  auto result = energy_cache.GetEnergyEvents(1, 1000, 2000);
  EXPECT_EQ(1, result.size());
  EXPECT_EQ(2000, result.at(0).timestamp());
}
