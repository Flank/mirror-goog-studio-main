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
#ifndef PERFD_NETWORK_TRAFFIC_SAMPLER_H_
#define PERFD_NETWORK_TRAFFIC_SAMPLER_H_

#include "perfd/network/network_sampler.h"

#include <string>

namespace profiler {

// Data collector of network traffic information. For example, it provides sent
// and received bytes of an app.
class TrafficSampler final : public NetworkSampler {
 public:
  TrafficSampler(const std::string &uid, const std::string &file)
      : uid_(uid), file_(file) {}

  // Reads traffic bytes sent and received, and store data in given {@code
  // NetworkProfilerData}.
  void GetData(profiler::proto::NetworkProfilerData *data) override;

 private:
  // App uid for parsing file to get app's traffic information.
  const std::string uid_;

  // Traffic file path.
  const std::string file_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_TRAFFIC_SAMPLER_H_
