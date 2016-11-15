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
#ifndef PERFD_EVENT_EVENT_PROFILER_SERVICE_H_
#define PERFD_EVENT_EVENT_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/event/event_cache.h"
#include "proto/event.grpc.pb.h"

namespace profiler {

class EventServiceImpl final : public profiler::proto::EventService::Service {
 public:
  explicit EventServiceImpl(EventCache* cache);

  // RPC Call that returns an array of event data scoped to the start and end
  // times passed in
  // to the request.
  grpc::Status GetActivityData(
      grpc::ServerContext* context,
      const profiler::proto::EventDataRequest* request,
      profiler::proto::ActivityDataResponse* response) override;
  grpc::Status GetSystemData(
      grpc::ServerContext* context,
      const profiler::proto::EventDataRequest* request,
      profiler::proto::SystemDataResponse* response) override;

  grpc::Status StartMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::EventStartRequest* request,
      profiler::proto::EventStartResponse* response) override;

  grpc::Status StopMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::EventStopRequest* request,
      profiler::proto::EventStopResponse* response) override;

 private:
  EventCache& cache_;
};

}  // namespace profiler

#endif  // PERFD_EVENT_EVENT_PROFILER_SERVICE_H_
