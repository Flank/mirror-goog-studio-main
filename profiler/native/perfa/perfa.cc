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
using profiler::Perfa;
using std::mutex;

Perfa* perfa_ = nullptr;
mutex perfa_mutex_;

// If perfa is disconnected from perfd, grpc requests will begin backing up.
// Given that downloading a 1MB image would send 1000 1K chunk messages (plus
// general network event messages), it seems reasonable to limit our queue to
// something one or two magnitudes above that, to be safe.
const int32_t kMaxBackgroundTasks = 100000;  // Worst case: ~100MB in memory
}

namespace profiler {

using proto::HeartBeatResponse;
using proto::InternalEventService;
using proto::InternalMemoryService;
using proto::InternalNetworkService;
using proto::PerfaControlRequest;
using proto::PerfaService;
using proto::CommonData;
using proto::RegisterApplication;
using std::lock_guard;

void Perfa::Initialize() {
  lock_guard<mutex> guard(perfa_mutex_);
  if (perfa_ == nullptr) perfa_ = new Perfa(kServerAddress);
}

Perfa& Perfa::Instance() {
  Initialize();
  return *perfa_;
}

Perfa::Perfa(const char* address)
    : background_queue_("Studio:Perfa", kMaxBackgroundTasks) {
  auto channel =
      grpc::CreateChannel(address, grpc::InsecureChannelCredentials());
  service_stub_ = PerfaService::NewStub(channel);
  event_stub_ = InternalEventService::NewStub(channel);
  memory_stub_ = InternalMemoryService::NewStub(channel);
  network_stub_ = InternalNetworkService::NewStub(channel);

  // Open the control stream
  RegisterApplication app_data;
  app_data.set_pid(getpid());
  control_stream_ = service_stub_->RegisterAgent(&control_context_, app_data);
  control_thread_ = std::thread(&Perfa::RunControlThread, this);

  // Open the component independent data stream
  data_stream_ = service_stub_->DataStream(&data_context_, &data_response_);

  // Enable the heartbeat.
  heartbeat_thread_ = std::thread(&Perfa::RunHeartbeatThread, this);
}

void Perfa::RunHeartbeatThread() {
  SetThreadName("HeartbeatThread");
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

void Perfa::RunControlThread() {
  SetThreadName("ControlThread");
  PerfaControlRequest request;
  while (control_stream_->Read(&request)) {
    // TODO: Process control request
  }
}

bool Perfa::WriteData(const CommonData& data) {
  return data_stream_->Write(data);
}

}  // namespace profiler
