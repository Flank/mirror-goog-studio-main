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
#include "energy_usage_sampler.h"

#include <gtest/gtest.h>

#include "proto/energy.pb.h"
#include "utils/fake_clock.h"
#include "utils/tokenizer.h"

using profiler::EnergyUsageSampler;
using profiler::FakeClock;
using profiler::Tokenizer;
using std::string;

TEST(VerifyRequiredHeading, ReturnsTrueForCorrectHeading) {
  string correct_heading{"9,12345,l,"};
  Tokenizer tokenizer(correct_heading, ",");

  FakeClock fakeClock;
  EnergyUsageSampler sampler(fakeClock);

  EXPECT_TRUE(sampler.VerifyRequiredHeading(tokenizer, 12345));
}

TEST(VerifyRequiredHeading, ReturnsFalseForEmptyHeading) {
  string incorrect_heading{""};
  Tokenizer tokenizer(incorrect_heading, ",");

  FakeClock fakeClock;
  EnergyUsageSampler sampler(fakeClock);

  EXPECT_FALSE(sampler.VerifyRequiredHeading(tokenizer, 12345));
}

TEST(VerifyRequiredHeading, ReturnsFalseForCorruptHeading) {
  string incorrect_heading{"9,1+lolwhat_cat"};
  Tokenizer tokenizer(incorrect_heading, ",");

  FakeClock fakeClock;
  EnergyUsageSampler sampler(fakeClock);

  EXPECT_FALSE(sampler.VerifyRequiredHeading(tokenizer, 12345));
}

TEST(VerifyRequiredHeading, ReturnsFalseForIncorrectUid) {
  string incorrect_heading{"9,0000000000000,l,"};
  Tokenizer tokenizer(incorrect_heading, ",");

  FakeClock fakeClock;
  EnergyUsageSampler sampler(fakeClock);

  EXPECT_FALSE(sampler.VerifyRequiredHeading(tokenizer, 12345));
}

TEST(ParseStatTokens, ParsesAndSavedTokensCorrectly) {
  profiler::proto::EnergyDataResponse::EnergySample sample;
  string cpu_stat_tokens{"cpu,100,200,300"};
  Tokenizer tokenizer(cpu_stat_tokens, ",");

  FakeClock fakeClock;
  EnergyUsageSampler sampler(fakeClock);

  sampler.ParseStatTokens(tokenizer, sample);

  EXPECT_EQ(sample.cpu_user_power_usage(), 100);
}
