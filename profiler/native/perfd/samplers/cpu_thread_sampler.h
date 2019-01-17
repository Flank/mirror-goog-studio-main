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
#ifndef PERFD_SAMPLERS_CPU_THREAD_SAMPLER_H_
#define PERFD_SAMPLERS_CPU_THREAD_SAMPLER_H_

#include <memory>
#include <string>
#include <unordered_map>

#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "proto/cpu_data.pb.h"
#include "utils/procfs_files.h"

namespace profiler {

class CpuThreadSampler final : public Sampler {
 public:
  CpuThreadSampler(const profiler::Session& session, Clock* clock,
                   EventBuffer* buffer)
      : CpuThreadSampler(session, clock, buffer, new ProcfsFiles) {}

  // ctor used by tests with a mocked ProcFiles.
  CpuThreadSampler(const profiler::Session& session, Clock* clock,
                   EventBuffer* buffer, ProcfsFiles* procfs)
      : Sampler(session, buffer, kSampleRateMs),
        pid_(session.info().pid()),
        procfs_(procfs) {}

  virtual void Sample() override;

 private:
  static constexpr const char* const kSamplerName = "CPU:Thread";
  static const int32_t kSampleRateMs = 200;

  virtual const char* name() override { return kSamplerName; }

  // PID of the app to be sampled.
  int32_t pid_;
  // Map from thread ID to last known thread state.
  std::unordered_map<int32_t, proto::CpuThreadData::State> previous_states_{};
  // Map from thread ID to last known thread name.
  std::unordered_map<int32_t, std::string> name_cache_{};
  // Files that are used to sample CPU threads.
  std::unique_ptr<const ProcfsFiles> procfs_;
};
}  // namespace profiler

#endif  // PERFD_SAMPLERS_CPU_THREAD_SAMPLER_H_
