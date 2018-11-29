/*
 * Copyright (C) 2018 The Android Open Source Project
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
#ifndef PERFD_SAMPLERS_NETWORK_CONNECTION_COUNT_SAMPLER_H_
#define PERFD_SAMPLERS_NETWORK_CONNECTION_COUNT_SAMPLER_H_

#include "perfd/network/connection_count_sampler.h"
#include "perfd/network/network_constants.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "utils/uid_fetcher.h"

namespace profiler {

// Wrapper for ConnectionCountSampler in the unified data pipeline.
class NetworkConnectionCountSampler final : public Sampler {
 public:
  NetworkConnectionCountSampler(const profiler::Session& session,
                                EventBuffer* buffer)
      : Sampler(session, buffer, kSampleRateMs),
        uid_(UidFetcher::GetUid(session.info().pid())),
        sampler_(NetworkConstants::GetConnectionFilePaths()) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "NET:CxnCount";
  static const int32_t kSampleRateMs = 500;

  virtual const char* name() override { return kSamplerName; }

  int32_t uid_;
  ConnectionCountSampler sampler_;
};

}  // namespace profiler

#endif  // PERFD_SAMPLERS_NETWORK_CONNECTION_COUNT_SAMPLER_H_
