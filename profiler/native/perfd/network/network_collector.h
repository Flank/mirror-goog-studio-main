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
#include <mutex>
#include <thread>
#include <unordered_map>
#include <vector>

namespace profiler {

typedef TimeValueBuffer<profiler::proto::NetworkProfilerData>
    NetworkProfilerBuffer;

// Class that runs in the background, continuously collecting network data
class NetworkCollector final {
 public:
  NetworkCollector(int sample_ms);
  ~NetworkCollector();

  // Allocates the given app's buffer and add it for all samplers to start.
  // If this is the first app, starts the collector's thread.
  void Start(int32_t pid, NetworkProfilerBuffer *buffer);

  // Remove the given app from all samplers and deallocate buffer to stop.
  // If this is the last app, stops the collector's thread.
  void Stop(int32_t pid);

 private:
  // Continually collects data on a background thread until stopped.
  void Collect();
  // Stores all started app's network data into buffers, this is called after
  // all samplers refreshed the data.
  void StoreDataToBuffer();

  // Sample frequency.
  int sample_us_;
  // Thread that network profile operations run on.
  std::thread profiler_thread_;
  // True if profile operations is running, false otherwise.
  std::atomic_bool is_running_{false};

  // First reads app uid from file, then creates app network data samplers;
  // collectors are saved into a vector member variable.
  std::vector<std::unique_ptr<NetworkSampler>> samplers_;
  // Mapping of app uid to its buffer. A new buffer is added into this map
  // when profiling for an app starts, and the buffer is removed when
  // its profiling stops. A buffer holds all of data including traffic bytes,
  // open connections, and device-wide radio power status.
  std::unordered_map<uint32_t, NetworkProfilerBuffer*> uid_to_buffers_;
  mutable std::mutex buffer_mutex_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_COLLECTOR_H_
