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
#include "energy_cache.h"
#include "proto/energy.pb.h"

#include <gtest/gtest.h>

using profiler::EnergyCache;

TEST(SaveEnergySample, SaveEnergySampleSavesCorrectly) {
  EnergyCache cache(2);
  profiler::proto::EnergyDataResponse::EnergySample sample;
  sample.set_timestamp(10000);
  sample.set_screen_power_usage(10001);
  sample.set_cpu_system_power_usage(10002);
  sample.set_cpu_user_power_usage(10003);
  sample.set_sensors_power_usage(10004);
  sample.set_cell_network_power_usage(10005);
  sample.set_wifi_network_power_usage(10006);

  cache.SaveEnergySample(sample);

  profiler::proto::EnergyDataResponse response;
  cache.LoadEnergyData(0, 20000, &response);
  profiler::proto::EnergyDataResponse::EnergySample response_sample;
  response_sample.CopyFrom(response.energy_samples(0));

  EXPECT_EQ(sample.timestamp(), response_sample.timestamp());
  EXPECT_EQ(sample.screen_power_usage(), response_sample.screen_power_usage());
  EXPECT_EQ(sample.cpu_system_power_usage(),
            response_sample.cpu_system_power_usage());
  EXPECT_EQ(sample.cpu_user_power_usage(),
            response_sample.cpu_user_power_usage());
  EXPECT_EQ(sample.sensors_power_usage(),
            response_sample.sensors_power_usage());
  EXPECT_EQ(sample.cell_network_power_usage(),
            response_sample.cell_network_power_usage());
  EXPECT_EQ(sample.wifi_network_power_usage(),
            response_sample.wifi_network_power_usage());
}

TEST(SaveEnergySample, SaveEnergySampleDiscardsOldSamples) {
  EnergyCache cache(2);
  profiler::proto::EnergyDataResponse::EnergySample sample_a;
  profiler::proto::EnergyDataResponse::EnergySample sample_b;
  profiler::proto::EnergyDataResponse::EnergySample sample_c;
  profiler::proto::EnergyDataResponse::EnergySample sample_d;

  sample_a.set_timestamp(10000);
  sample_b.set_timestamp(10002);
  sample_c.set_timestamp(10003);
  sample_d.set_timestamp(10004);

  cache.SaveEnergySample(sample_a);
  cache.SaveEnergySample(sample_b);
  cache.SaveEnergySample(sample_c);

  {
    profiler::proto::EnergyDataResponse response;
    cache.LoadEnergyData(0, 20000, &response);
    profiler::proto::EnergyDataResponse::EnergySample response_sample;
    response_sample.CopyFrom(response.energy_samples(0));

    EXPECT_EQ(sample_b.timestamp(), response.energy_samples(0).timestamp());
    EXPECT_EQ(sample_c.timestamp(), response.energy_samples(1).timestamp());
  }

  cache.SaveEnergySample(sample_d);

  {
    profiler::proto::EnergyDataResponse response;
    cache.LoadEnergyData(0, 20000, &response);
    profiler::proto::EnergyDataResponse::EnergySample response_sample;
    response_sample.CopyFrom(response.energy_samples(0));

    EXPECT_EQ(sample_c.timestamp(), response.energy_samples(0).timestamp());
    EXPECT_EQ(sample_d.timestamp(), response.energy_samples(1).timestamp());
  }
}
