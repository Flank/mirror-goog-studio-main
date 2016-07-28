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
#ifndef ENERGY_USAGE_SAMPLER_H_
#define ENERGY_USAGE_SAMPLER_H_

#include "proto/energy.pb.h"
#include "utils/clock.h"
#include "utils/tokenizer.h"

#include <string>

namespace profiler {

// Samples device energy usage data and pack them in protos. This sampler
// currently gets its data by parsing the output of dumpsys batterystats, which
// is quite slow. A better implementation will be needed in the future to
// provide a faster and lighter sampling method.
class EnergyUsageSampler final {
 public:
  explicit EnergyUsageSampler(const Clock& clock) : clock_(clock) {}
  ~EnergyUsageSampler() = default;

  // Gets available energy stats for the process with given pid. If a stat is
  // not available, the field will not be set.
  const void GetProcessEnergyUsage(
      const int pid, proto::EnergyDataResponse_EnergySample& sample);

  // Parses a series of stat tokens given a tokenizer at the beginning of the
  // category token; For example, the following are the stat tokens for CPU:
  //
  //    cpu,1000,1234,4567
  //
  // It will then put the parsed stats into it's appropriate section in the
  // specified sample. If no recognized stats can be found, the sample will be
  // left untouched.
  const void ParseStatTokens(Tokenizer& tokenizer,
                             proto::EnergyDataResponse_EnergySample& sample);

  // Checks that a line begins with the required heading. A required heading
  // begins with the following:
  //
  //    #,<required_uid>,l,
  //
  // If heading matches the required format, the tokenizer will be left right
  // before the category tag. If the required format does not match, the
  // tokenizer should be discarded and move on to the next line of input.
  const bool VerifyRequiredHeading(Tokenizer& tokenizer,
                                   const int32_t required_uid);

 private:
  const Clock& clock_;
};

}  // namespace profiler

#endif  // ENERGY_USAGE_SAMPLER_H_
