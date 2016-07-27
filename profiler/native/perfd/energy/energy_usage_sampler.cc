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

#include <cstdlib>
#include <sstream>
#include <string>

#include "utils/tokenizer.h"

// TODO required work:
// - Report actual power usage instead of component time count (power_profiles)
// - Add rest of the required stats to the sample
namespace {
static constexpr const char* kDumpsysBatterystatsCommand =
    "dumpsys batterystats -c";
static constexpr const int kLineBufferSize = 1024;

// TODO remove this when std::stoi is available.
static const int32_t temp_stoi(const std::string& s) {
  std::istringstream stream(s);
  int32_t i;
  stream >> i;
  return i;
}
}

namespace profiler {

using std::string;

const bool EnergyUsageSampler::VerifyRequiredHeading(
    Tokenizer& tokenizer, const int32_t required_uid) {
  string log_type;
  string uid;
  return (tokenizer.EatNextToken(Tokenizer::IsDigit) &&
          tokenizer.GetNextToken(&uid) && temp_stoi(uid) == required_uid &&
          tokenizer.GetNextToken(&log_type) && log_type.compare("l") == 0);
}

const void EnergyUsageSampler::ParseStatTokens(
    Tokenizer& tokenizer, proto::EnergyDataResponse_EnergySample& sample) {
  std::string category;
  tokenizer.GetNextToken(&category);

  // TODO add sampling for remaining stats defined in proto.
  // Compares the category token to the required categories and further
  // extract stats if the category is correct. The format in each block
  // below is the tokens after the category token.
  // e.g.:
  //    line from dumpsys:  9,10087,l,cpu,223989,107626,3988814
  //    starting cursor: ~~~~~~~~~~~~~~~~~^
  if (category.compare("cpu") == 0) {
    // Format: user-cpu-time-ms, system-cpu-time-ms, total-cpu-power-mAus
    string user_time_token;
    string system_time_token;
    tokenizer.GetNextToken(&user_time_token);
    tokenizer.GetNextToken(&system_time_token);
    int32_t user_time = temp_stoi(user_time_token);
    int32_t system_time = temp_stoi(system_time_token);
    sample.set_cpu_user_power_usage(user_time);
    sample.set_cpu_system_power_usage(system_time);

  } else if (category.compare("fg") == 0) {
    // Format: total-time, start-count
    string foreground_time_token;
    tokenizer.GetNextToken(&foreground_time_token);
    int32_t foreground_time = temp_stoi(foreground_time_token);
    sample.set_screen_power_usage(foreground_time);
  }
}

const void EnergyUsageSampler::GetProcessEnergyUsage(
    const int pid, proto::EnergyDataResponse_EnergySample& sample) {
  std::unique_ptr<FILE, int (*)(FILE*)> dump_file(
      popen(kDumpsysBatterystatsCommand, "r"), pclose);

  if (!dump_file) {
    // TODO Error handling.
    return;
  }

  sample.set_timestamp(clock_.GetCurrentTime());

  char line_buffer[kLineBufferSize];
  while (!feof(dump_file.get()) &&
         fgets(line_buffer, kLineBufferSize, dump_file.get()) != nullptr) {
    Tokenizer tokenizer(line_buffer, ",");
    if (VerifyRequiredHeading(tokenizer, pid)) {
      ParseStatTokens(tokenizer, sample);
    }
  }
}
}
