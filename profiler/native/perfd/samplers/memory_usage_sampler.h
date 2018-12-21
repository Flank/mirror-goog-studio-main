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
#ifndef PERFD_SAMPLERS_MEMORY_USAGE_SAMPLER_H_
#define PERFD_SAMPLERS_MEMORY_USAGE_SAMPLER_H_

#include "perfd/memory/memory_usage_reader_impl.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"

namespace profiler {

class MemoryUsageSampler final : public Sampler {
 public:
  MemoryUsageSampler(const profiler::Session& session, Clock* clock,
                     EventBuffer* buffer)
      : MemoryUsageSampler(session, clock, buffer,
                           new MemoryUsageReaderImpl()) {}

  // Allow for injection of MemoryUsageReader for testing.
  MemoryUsageSampler(const profiler::Session& session, Clock* clock,
                     EventBuffer* buffer, MemoryUsageReader* reader)
      : Sampler(session, buffer, kSampleRateMs),
        pid_(session.info().pid()),
        reader_(reader) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "MEM:Usage";
  static const int32_t kSampleRateMs = 500;

  virtual const char* name() override { return kSamplerName; }

  // PID of the app to be sampled.
  int32_t pid_;
  std::unique_ptr<MemoryUsageReader> reader_;
};

}  // namespace profiler

#endif  // PERFD_SAMPLERS_MEMORY_USAGE_SAMPLER_H_
