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
#ifndef PERFD_NETWORK_NETWORK_COLLECTOR_H_
#define PERFD_NETWORK_NETWORK_COLLECTOR_H_

#include "perfd/network/network_sampler.h"
#include "proto/network.pb.h"
#include "utils/time_value_buffer.h"

#include <atomic>
#include <memory>
#include <thread>
#include <vector>

namespace profiler {

typedef TimeValueBuffer<profiler::proto::NetworkProfilerData>
    NetworkProfilerBuffer;

// Class that runs in the background, continuously collecting network data
class NetworkCollector final {
 public:
  // Each collector for pid specific information, if pid is -1, it is device
  // network information.
  NetworkCollector(int pid, int sample_ms, NetworkProfilerBuffer* buffer)
      : pid_(pid), sample_us_(sample_ms * 1000), buffer_(*buffer) {}
  ~NetworkCollector();

  // Creates a thread that collects and saves network data continually.
  void Start();

  // Stops collecting data and wait for thread exit.
  void Stop();

  // Return app id that this network collector is for, -1 if for any app.
  int pid();

 private:
  // First reads app uid from file, then creates app network data samplers;
  // collectors are saved into a vector member variable.
  void CreateSamplers();

  // Continually collects data on a background thread until stopped.
  void Collect();

  // App pid.
  int pid_;
  // Sample frequency.
  int sample_us_;
  // Buffer that holds sample data so far.
  NetworkProfilerBuffer& buffer_;
  // Thread that network profile operations run on.
  std::thread profiler_thread_;
  // True if profile operations is running, false otherwise.
  std::atomic_bool is_running_;
  // Vector to hold data collectors which may need some steps to create.
  std::vector<std::unique_ptr<NetworkSampler>> samplers_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_COLLECTOR_H_
