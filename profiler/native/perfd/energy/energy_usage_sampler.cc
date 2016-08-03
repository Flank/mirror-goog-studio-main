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

using std::istringstream;
using std::string;

// TODO required work:
// - Report actual power usage instead of component time count (power_profiles)
// - Add rest of the required stats to the sample
namespace {
static constexpr const char* kDumpsysBatterystatsCommand =
    "dumpsys batterystats -c";
static constexpr const int kLineBufferSize = 1024;

// TODO remove this when std::stoi is available.
// Tries to convert given string to it's represented int value.
// Returns 0 if it fails.
static const int32_t temp_stoi(const string& s) {
  istringstream stream(s);
  int32_t i;
  if (!(stream >> i)) {  // If conversion fails.
    i = 0;
  }
  return i;
}

// Gets the next token and try to convert it to int before returning it.
// Returns 0 if there is no next token or next token is not an int.
static const int32_t getNextInteger(profiler::Tokenizer* tokenizer) {
  string token;
  tokenizer->GetNextToken(&token);
  return temp_stoi(token);
}

}  // namespace

namespace profiler {

const bool EnergyUsageSampler::VerifyRequiredHeading(
    Tokenizer* tokenizer, const int32_t required_uid) {
  string log_type;
  string uid;
  return (tokenizer->EatNextToken(Tokenizer::IsDigit) &&
          tokenizer->GetNextToken(&uid) && temp_stoi(uid) == required_uid &&
          tokenizer->GetNextToken(&log_type) && log_type.compare("l") == 0);
}

const void EnergyUsageSampler::ParseStatTokens(
    Tokenizer* tokenizer, proto::EnergyDataResponse_EnergySample* sample) {
  std::string category;
  tokenizer->GetNextToken(&category);

  // TODO add sampling for remaining stats defined in proto.
  // Compares the category token to the required categories and further
  // extract stats if the category is correct. The format in each block
  // below is the tokens after the category token.
  // e.g.:
  //    line from dumpsys:  9,10087,l,cpu,223989,107626,3988814
  //    starting cursor: ~~~~~~~~~~~~~~~~~^
  if (category.compare("cpu") == 0) {
    // CPU time from CPU (cpu) stat category.
    // Format: user-cpu-time-ms, system-cpu-time-ms, total-cpu-power-mAus
    sample->set_cpu_user_power_usage(getNextInteger(tokenizer));
    sample->set_cpu_system_power_usage(getNextInteger(tokenizer));

  } else if (category.compare("fg") == 0) {
    // App screen-on time from foreground (fg) category.
    // Format: total-time-ms, start-count
    sample->set_screen_power_usage(getNextInteger(tokenizer));

  } else if (category.compare("wfcd") == 0) {
    // Wifi usage time from wifi-controller-data (wfcd) category.
    // Format: wifi-idle-time-ms, wifi-rx-time-ms,
    //         wifi-power-counter (unreliable), {wifi-tx-time-ms}+
    int32_t wifi_usage_ms = 0;
    tokenizer->EatNextToken();
    wifi_usage_ms += getNextInteger(tokenizer);  // rx time.
    tokenizer->EatNextToken();

    string wifi_tx_times;
    while (tokenizer->GetNextToken(&wifi_tx_times)) {
      // The reason why this loop exists is because android has many Tx
      // power-level buckets, and depending on factors such as signal strength
      // the system may decide to use high-power mode to boost Tx signal
      // strength. This is not the case for Rx because Rx has only one.
      //
      // Currently we simply return the sum of the time spent in these buckets,
      // which is not an accurate representation of the amount of power used.
      // When power_profiles.xml becomes available, these should be multiplied
      // to the proper bucket power values to accurately represent power usage.
      wifi_usage_ms += temp_stoi(wifi_tx_times);
    }

    sample->set_wifi_network_power_usage(wifi_usage_ms);

  } else if (category.compare("mcd") == 0) {
    // Modem (radio) usage time from modem-controller-data (mcd) category.
    // Format: modem-idle-time-ms, modem-rx-time-ms,
    //         modem-power-counter (unreliable), {modem-tx-time-ms}+
    int32_t modem_usage_ms = 0;
    tokenizer->EatNextToken();
    modem_usage_ms += getNextInteger(tokenizer);  // rx time.
    tokenizer->EatNextToken();

    string modem_tx_times;
    while (tokenizer->GetNextToken(&modem_tx_times)) {
      // The reason why this loop exists is because android has many Tx
      // power-level buckets, and depending on factors such as signal strength
      // the system may decide to use high-power mode to boost Tx signal
      // strength. This is not the case for Rx because Rx has only one.
      //
      // Currently we simply return the sum of the time spent in these buckets,
      // which is not an accurate representation of the amount of power used.
      // When power_profiles.xml becomes available, these should be multiplied
      // to the proper bucket power values to accurately represent power usage.
      modem_usage_ms += temp_stoi(modem_tx_times);
    }

    sample->set_cell_network_power_usage(modem_usage_ms);
  }
}

const void EnergyUsageSampler::GetProcessEnergyUsage(
    const int pid, proto::EnergyDataResponse_EnergySample* sample) {
  std::unique_ptr<FILE, int (*)(FILE*)> dump_file(
      popen(kDumpsysBatterystatsCommand, "r"), pclose);

  if (!dump_file) {
    // TODO Error handling.
    return;
  }

  sample->set_timestamp(clock_.GetCurrentTime());

  char line_buffer[kLineBufferSize];
  while (!feof(dump_file.get()) &&
         fgets(line_buffer, kLineBufferSize, dump_file.get()) != nullptr) {
    Tokenizer tokenizer(line_buffer, ",");
    if (VerifyRequiredHeading(&tokenizer, pid)) {
      ParseStatTokens(&tokenizer, sample);
    }
  }
}
}  // namespace profiler
