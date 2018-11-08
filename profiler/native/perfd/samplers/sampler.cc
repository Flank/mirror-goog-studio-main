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
#include "sampler.h"

#include <unistd.h>
#include <cassert>
#include <thread>

#include "utils/clock.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

namespace profiler {

Sampler::Sampler(const profiler::Session& session, EventBuffer* buffer,
                 int64_t sample_interval_ms)
    : session_(session),
      buffer_(buffer),
      sample_interval_ns_(Clock::ms_to_ns(sample_interval_ms)),
      is_running_(false) {}

Sampler::~Sampler() { Stop(); }

void Sampler::Start() {
  if (!is_running_.exchange(true)) {
    // the worker thread should not be running in this case.
    assert(!sampling_thread_.joinable());
    sampling_thread_ = std::thread(&Sampler::SamplingThread, this);
  }
}

void Sampler::Stop() {
  if (is_running_.exchange(false)) {
    assert(sampling_thread_.joinable());
    sampling_thread_.join();
  }
}

void Sampler::SamplingThread() {
  SetThreadName(name());
  while (is_running_.load()) {
    Stopwatch stopwatch;
    int64_t start_ns = stopwatch.GetElapsed();
    Trace::Begin(name());
    Sample();
    Trace::End();
    int64_t elapsed_ns = stopwatch.GetElapsed() - start_ns;
    if (sample_interval_ns_ > elapsed_ns) {
      int64_t sleep_us = Clock::ns_to_us(sample_interval_ns_ - elapsed_ns);
      usleep(static_cast<uint64_t>(sleep_us));
    }
  }
}

}  // namespace profiler
