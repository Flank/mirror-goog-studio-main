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
#ifndef PERFD_SAMPLERS_AGENT_SAMPLER_H_
#define PERFD_SAMPLERS_AGENT_SAMPLER_H_

#include "perfd/daemon.h"
#include "perfd/samplers/sampler.h"

namespace profiler {

// Wrapper for getAgentStatus in the unified data pipeline.
class AgentStatusSampler final : public Sampler {
 public:
  AgentStatusSampler(const profiler::Session& session, Daemon* daemon)
      : Sampler(session, daemon->buffer(), kSampleRateMs),
        daemon_(daemon),
        last_agent_status_(proto::AgentData::UNSPECIFIED) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "PROFILER:Agent";
  static const int32_t kSampleRateMs = 500;

  virtual const char* name() override { return kSamplerName; }
  Daemon* daemon_;
  proto::AgentData::Status last_agent_status_;
};

}  // namespace profiler

#endif  // PERFD_SAMPLERS_AGENT_SAMPLER_H_