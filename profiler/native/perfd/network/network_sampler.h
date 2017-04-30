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
#ifndef PERFD_NETWORK_NETWORK_SAMPLER_H_
#define PERFD_NETWORK_NETWORK_SAMPLER_H_

#include "proto/network.pb.h"

namespace profiler {

using proto::NetworkProfilerData;

// Abstract network data collector.
class NetworkSampler {
 public:
  virtual ~NetworkSampler() = default;

  // Refresh data for this sampler for all apps. After this is called, collected
  // data are stored in sampler internally.
  virtual void Refresh() = 0;

  // |Sample| returns collected data of a given app from the last refresh call.
  // It is be called once per app we are profiling, and each call is expected
  // to use the same data collected by the latest refresh.
  virtual NetworkProfilerData Sample(const uint32_t uid) = 0;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_SAMPLER_H_
