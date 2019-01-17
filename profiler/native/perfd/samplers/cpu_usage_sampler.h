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
#ifndef PERFD_SAMPLERS_CPU_USAGE_SAMPLER_H_
#define PERFD_SAMPLERS_CPU_USAGE_SAMPLER_H_

#include "perfd/cpu/cpu_usage_sampler.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "utils/procfs_files.h"

namespace profiler {

// Wrapper for CpuUsageSampler in the unified data pipeline.
// TODO: rename to CpuUsageSampler once cpu/cpu_usage_sampler is removed.
class CpuUsageDataSampler final : public Sampler {
 public:
  CpuUsageDataSampler(const profiler::Session& session, Clock* clock,
                      EventBuffer* buffer)
      : CpuUsageDataSampler(
            session, buffer,
            // We don't use CpuCache in the new pipeline so passing in null.
            new CpuUsageSampler(clock, nullptr)) {}

  // Constructor used by tests to mock the wrapped CpuUsageSampler.
  CpuUsageDataSampler(const profiler::Session& session, EventBuffer* buffer,
                      CpuUsageSampler* usage_sampler)
      : Sampler(session, buffer, kSampleRateMs),
        pid_(session.info().pid()),
        usage_sampler_(usage_sampler) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "CPU:Usage";
  static const int32_t kSampleRateMs = 200;

  virtual const char* name() override { return kSamplerName; }

  // PID of the app to be sampled.
  int32_t pid_;
  // Wrapped CpuUsageSampler that collects CPU usage data.
  std::unique_ptr<CpuUsageSampler> usage_sampler_;
};

}  // namespace profiler

#endif  // PERFD_SAMPLERS_CPU_USAGE_SAMPLER_H_
