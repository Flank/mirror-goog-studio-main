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
#include "connectivity_sampler.h"
#include "test/utils.h"
#include "utils/bash_command.h"

#include <gtest/gtest.h>

using profiler::ConnectivitySampler;
using profiler::TestUtils;

TEST(ConnectivitySampler, RadioActive) {
  ConnectivitySampler sampler(
    "cat " + TestUtils::getNetworkTestData("connectivity_radio_active.txt"),
    "cat " + TestUtils::getNetworkTestData(
        "connectivity_network_type_mobile.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::ACTIVE,
            data.connectivity_data().radio_state());
}

TEST(ConnectivitySampler, RadioIsDefaultValueWhenNetworkIsWifi) {
  ConnectivitySampler sampler(
    "cat " + TestUtils::getNetworkTestData("connectivity_radio_active.txt"),
    "cat " + TestUtils::getNetworkTestData("connectivity_network_type_wifi.txt")
  );
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(0, data.connectivity_data().radio_state());
}

TEST(ConnectivitySampler, RadioSleeping) {
  ConnectivitySampler sampler(
    "cat " + TestUtils::getNetworkTestData("connectivity_radio_sleeping.txt"),
    "cat " + TestUtils::getNetworkTestData(
        "connectivity_network_type_mobile.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::SLEEPING,
            data.connectivity_data().radio_state());
}

TEST(ConnectivitySampler, NoRadioState) {
  ConnectivitySampler sampler(
    "cat " + TestUtils::getNetworkTestData("connectivity_radio_missing.txt"),
    "cat " + TestUtils::getNetworkTestData(
        "connectivity_network_type_mobile.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::UNSPECIFIED,
            data.connectivity_data().radio_state());
}

TEST(ConnectivitySampler, RadioStateTrueAndFalseMatch) {
  ConnectivitySampler sampler("cat " + TestUtils::getNetworkTestData(
    "connectivity_radio_both_false_and_true_exist.txt"),
    "cat " + TestUtils::getNetworkTestData(
        "connectivity_network_type_mobile.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::SLEEPING,
            data.connectivity_data().radio_state());
}

TEST(ConnectivitySampler, NoNetworkTypeId) {
  ConnectivitySampler sampler("", "cat " + TestUtils::getNetworkTestData(
    "connectivity_no_network_type_id.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::INVALID,
            data.connectivity_data().default_network_type());
}

TEST(ConnectivitySampler, NetworkTypeWifi) {
  ConnectivitySampler sampler("", "cat " + TestUtils::getNetworkTestData(
    "connectivity_network_type_wifi.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::WIFI,
            data.connectivity_data().default_network_type());
}

TEST(ConnectivitySampler, NetworkTypeMobile) {
  ConnectivitySampler sampler("", "cat " + TestUtils::getNetworkTestData(
    "connectivity_network_type_mobile.txt"));
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::MOBILE,
            data.connectivity_data().default_network_type());
}
