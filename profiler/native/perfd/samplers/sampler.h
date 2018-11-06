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
#ifndef PERFD_SAMPLERS_SAMPLER_H_
#define PERFD_SAMPLERS_SAMPLER_H_

#include <atomic>
#include <string>
#include <thread>

#include "perfd/event_buffer.h"
#include "perfd/sessions/session.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/log.h"

namespace profiler {

// Base class of the sampling infrastructure in the profilers' new data
// pipeline. It runs a worker thread that calls into Sample() at regular
// intervals as specified in |sample_interval_ms|. Subclasses are expected to
// implement the sampling logic based on the |session| that is currently being
// profiled, and to insert the data into the event buffer.
class Sampler {
 public:
  Sampler(const profiler::Session& session, EventBuffer* buffer,
          int64_t sample_interval_ms);
  virtual ~Sampler();

  // Start the sampling worker thread. No-op if the thread is started already
  void Start();

  // Stops the sampling worker thread. No-op if the thread has not been started.
  void Stop();

  // Collect data related to the session that s currently being sampled.
  virtual void Sample() {}

 protected:
  const profiler::Session& session() const { return session_; }
  EventBuffer* buffer() { return buffer_; }

 private:
  // The worker thread for sampling. Note that the thread is started upon
  // construction of the sampler instace, so no explicit start is required.
  void SamplingThread();

  // For debugging purposes - used for setting the sampling threads name and
  // inserting systrace markers.
  virtual const char* name() { return ""; }

  const profiler::Session& session_;
  EventBuffer* buffer_;
  int64_t sample_interval_ns_;
  std::atomic_bool is_running_;
  std::thread sampling_thread_;
};
}  // namespace profiler

#endif  // PERFD_SAMPLERS_SAMPLER_H_
