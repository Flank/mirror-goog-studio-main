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

namespace {
using profiler::Perfa;
using std::mutex;

Perfa* perfa_ = nullptr;
mutex perfa_mutex_;
}

namespace profiler {

using proto::InternalMemoryService;
using proto::InternalNetworkService;
using proto::InternalEventService;
using proto::PerfaControlRequest;
using proto::PerfaService;
using proto::ProfilerData;
using proto::RegisterApplication;
using std::lock_guard;
using std::mutex;

void Perfa::Initialize() {
  lock_guard<mutex> guard(perfa_mutex_);
  if (perfa_ == nullptr) perfa_ = new Perfa(kServerAddress);
}

Perfa& Perfa::Instance() {
  Initialize();
  return *perfa_;
}

Perfa::Perfa(const char* address) {
  auto channel =
      grpc::CreateChannel(address, grpc::InsecureChannelCredentials());
  service_stub_ = PerfaService::NewStub(channel);
  memory_stub_ = InternalMemoryService::NewStub(channel);
  network_stub_ = InternalNetworkService::NewStub(channel);
  event_stub_ = InternalEventService::NewStub(channel);

  // Open the control stream
  RegisterApplication app_data;
  app_data.set_pid(getpid());
  control_stream_ = service_stub_->RegisterAgent(&control_context_, app_data);
  control_thread_ = std::thread(&Perfa::RunControlThread, this);

  // Open the component independent data stream
  data_stream_ = service_stub_->DataStream(&data_context_, &data_response_);
}

void Perfa::RunControlThread() {
  PerfaControlRequest request;
  while (control_stream_->Read(&request)) {
    // TODO: Process control request
  }
}

bool Perfa::WriteData(const ProfilerData& data) {
  return data_stream_->Write(data);
}

}  // namespace profiler