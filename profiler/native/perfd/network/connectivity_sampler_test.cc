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
#include "fake_network_type_provider.h"
#include "test/utils.h"
#include "utils/bash_command.h"

#include <gtest/gtest.h>
#include <memory>

using profiler::ConnectivitySampler;
using profiler::FakeNetworkTypeProvider;
using profiler::TestUtils;

TEST(ConnectivitySampler, NetworkTypeMobileFromProvider) {
  auto network_type_mobile = std::make_shared<FakeNetworkTypeProvider>(
      profiler::proto::ConnectivityData::MOBILE);
  ConnectivitySampler sampler(network_type_mobile);
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::MOBILE,
            data.connectivity_data().network_type());
}

TEST(ConnectivitySampler, NetworkTypeWifiFromProvider) {
  auto network_type_wifi = std::make_shared<FakeNetworkTypeProvider>(
      profiler::proto::ConnectivityData::WIFI);
  ConnectivitySampler sampler(network_type_wifi);
  sampler.Refresh();
  auto data = sampler.Sample();
  EXPECT_TRUE(data.has_connectivity_data());
  EXPECT_EQ(profiler::proto::ConnectivityData::WIFI,
            data.connectivity_data().network_type());
}
