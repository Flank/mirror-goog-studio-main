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
#include "perfa.h"

#include <sys/types.h>
#include <unistd.h>
#include <mutex>

#include "utils/config.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"

namespace {

// If perfa is disconnected from perfd, grpc requests will begin backing up.
// Given that downloading a 1MB image would send 1000 1K chunk messages (plus
// general network event messages), it seems reasonable to limit our queue to
// something one or two magnitudes above that, to be safe.
const int32_t kMaxBackgroundTasks = 100000;  // Worst case: ~100MB in memory

}  // namespace

namespace profiler {

using proto::HeartBeatResponse;
using proto::InternalEventService;
using proto::InternalMemoryService;
using proto::InternalNetworkService;
using proto::PerfaService;
using proto::CommonData;
using std::lock_guard;

Perfa& Perfa::Instance() {
  static Perfa* instance = new Perfa(kServerAddress);
  return *instance;
}

Perfa::Perfa(const char* address)
    : background_queue_("Studio:Perfa", kMaxBackgroundTasks) {
  auto channel =
      grpc::CreateChannel(address, grpc::InsecureChannelCredentials());
  service_stub_ = PerfaService::NewStub(channel);
  event_stub_ = InternalEventService::NewStub(channel);
  memory_stub_ = InternalMemoryService::NewStub(channel);
  network_stub_ = InternalNetworkService::NewStub(channel);

  // Enable the heartbeat.
  heartbeat_thread_ = std::thread(&Perfa::RunHeartbeatThread, this);
}

void Perfa::RunHeartbeatThread() {
  SetThreadName("Studio:Heartbeat");
  Stopwatch stopwatch;
  while (true) {
    int64_t start_ns = stopwatch.GetElapsed();
    // TODO: handle erroneous status
    // TODO: set deadline to check if perfd is alive.
    HeartBeatResponse response;
    grpc::ClientContext context;
    CommonData data;
    data.set_process_id(getpid());
    grpc::Status status = service_stub_->HeartBeat(&context, data, &response);
    int64_t elapsed_ns = stopwatch.GetElapsed() - start_ns;
    if (kHeartBeatIntervalNs > elapsed_ns) {
      int64_t sleep_us = Clock::ns_to_us(kHeartBeatIntervalNs - elapsed_ns);
      usleep(static_cast<uint64_t>(sleep_us));
    }
  }
}

}  // namespace profiler
