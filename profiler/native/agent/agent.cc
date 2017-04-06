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
#include "agent.h"

#include <sys/types.h>
#include <unistd.h>
#include <mutex>

#include <grpc++/support/channel_arguments.h>
#include "utils/config.h"
#include "utils/log.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"

namespace {

// If the agent is disconnected from perfd, grpc requests will begin backing up.
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
using proto::AgentService;
using proto::CommonData;
using std::lock_guard;

Agent& Agent::Instance() {
  static Agent* instance = new Agent(kServerAddress);
  return *instance;
}

Agent::Agent(const char* address)
    : background_queue_("Studio:Agent", kMaxBackgroundTasks) {
  // Override default channel arguments in gRPC to limit the reconnect delay to
  // 1 second when the daemon is unavailable. GRPC's default arguments may
  // have backoff as long as 120 seconds. In cases when the phone is
  // disconnected and plugged back in, a 120-second-long delay hurts the user
  // experience very much.
  grpc::ChannelArguments channel_args;
  channel_args.SetInt(GRPC_ARG_MAX_RECONNECT_BACKOFF_MS, 1000);
  auto channel =
      grpc::CreateCustomChannel(address, grpc::InsecureChannelCredentials(),
                                channel_args);
  service_stub_ = AgentService::NewStub(channel);
  event_stub_ = InternalEventService::NewStub(channel);
  memory_stub_ = InternalMemoryService::NewStub(channel);
  network_stub_ = InternalNetworkService::NewStub(channel);

  // Enable the heartbeat.
  heartbeat_thread_ = std::thread(&Agent::RunHeartbeatThread, this);
}

void Agent::AddPerfdStatusChangedCallback(PerfdStatusChanged callback) {
  lock_guard<std::mutex> guard(callback_mutex_);
  perfd_status_changed_callbacks_.push_back(callback);
}

void Agent::RunHeartbeatThread() {
  SetThreadName("Studio:Heartbeat");
  Stopwatch stopwatch;
  bool was_perfd_alive = false;
  while (true) {
    int64_t start_ns = stopwatch.GetElapsed();
    // TODO: handle erroneous status
    HeartBeatResponse response;
    grpc::ClientContext context;

    // Set a deadline on the context, so we can get a proper status code if
    // perfd is not connected.
    std::chrono::nanoseconds offset(kHeartBeatIntervalNs * 2);
    std::chrono::system_clock::time_point deadline = std::chrono::system_clock::now();

    // Linux and Mac are slightly different with respect to default accuracy of time_point
    // Linux is nanoseconds, mac is milliseconds so we cater to the lowest common.
    deadline += std::chrono::duration_cast<std::chrono::milliseconds>(offset);
    context.set_deadline(deadline);
    CommonData data;
    data.set_process_id(getpid());

    // Status returns OK if it succeeds, else it returns a standard grpc error
    // code.
    const grpc::Status status =
        service_stub_->HeartBeat(&context, data, &response);
    int64_t elapsed_ns = stopwatch.GetElapsed() - start_ns;
    // Use status to determine if perfd is alive.
    bool is_perfd_alive = status.ok();
    if (kHeartBeatIntervalNs > elapsed_ns) {
      int64_t sleep_us = Clock::ns_to_us(kHeartBeatIntervalNs - elapsed_ns);
      usleep(static_cast<uint64_t>(sleep_us));
    }

    if (is_perfd_alive != was_perfd_alive) {
      lock_guard<std::mutex> guard(callback_mutex_);
      for (auto callback : perfd_status_changed_callbacks_) {
        callback(is_perfd_alive);
      }
      was_perfd_alive = is_perfd_alive;
    }
  }
}

}  // namespace profiler
