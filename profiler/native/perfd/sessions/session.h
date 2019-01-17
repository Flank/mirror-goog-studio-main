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
#ifndef PERFD_SESSIONS_SESSION_H
#define PERFD_SESSIONS_SESSION_H

#include <memory>
#include <vector>

#include "perfd/samplers/sampler.h"
#include "proto/common.pb.h"

namespace profiler {

class Daemon;

// A profiling session on a specific process on a specific device.
class Session final {
 public:
  Session(int64_t device_id, int32_t pid, int64_t start_timestamp,
          Daemon* daemon);

  bool IsActive() const;

  void StartSamplers();
  void StopSamplers();

  // Mark the session as ended. Also stops the samplers in the process.
  bool End(int64_t timestamp);

  const proto::Session& info() const { return info_; }

  // Visible for testing.
  const std::vector<std::unique_ptr<Sampler>>& samplers() const {
    return samplers_;
  }
  std::vector<std::unique_ptr<Sampler>>& samplers() { return samplers_; }

 private:
  proto::Session info_;
  // Samplers used for the unified data pipeline.
  std::vector<std::unique_ptr<Sampler>> samplers_;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSION_H
