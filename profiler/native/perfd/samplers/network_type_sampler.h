/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef PERFD_SAMPLERS_NETWORK_TYPE_SAMPLER_H_
#define PERFD_SAMPLERS_NETWORK_TYPE_SAMPLER_H_

#include <memory>
#include "perfd/network/io_network_type_provider.h"
#include "perfd/network/network_type_provider.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"

namespace profiler {

// Wrapper for NetworkTypeProvider in the unified data pipeline.
class NetworkTypeSampler final : public Sampler {
 public:
  NetworkTypeSampler(const profiler::Session& session, EventBuffer* buffer)
      : NetworkTypeSampler(session, buffer,
                           std::make_shared<IoNetworkTypeProvider>()) {}

  // Visible for Testing
  NetworkTypeSampler(const profiler::Session& session, EventBuffer* buffer,
                     std::shared_ptr<NetworkTypeProvider> network_type_provider)
      : Sampler(session, buffer, kSampleRateMs),
        network_type_provider_(network_type_provider) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "NET:Type";
  static const int32_t kSampleRateMs = 500;

  virtual const char* name() override { return kSamplerName; }

  std::shared_ptr<NetworkTypeProvider> network_type_provider_;
};

}  // namespace profiler

#endif  // PERFD_SAMPLERS_NETWORK_TYPE_SAMPLER_H_
