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
#ifndef PERFD_EVENT_INTERNAL_EVENT_SERVICE_H_
#define PERFD_EVENT_INTERNAL_EVENT_SERVICE_H_

#include <grpc++/grpc++.h>
#include "perfd/event/event_cache.h"
#include "proto/event.grpc.pb.h"
#include "proto/internal_event.grpc.pb.h"

namespace profiler {

class InternalEventServiceImpl final
    : public profiler::proto::InternalEventService::Service {
 public:
  explicit InternalEventServiceImpl(EventCache& cache);

  // RPC to store events in the event data cache. Incoming event data should
  // have
  // their ProfilerData::end_timestamp field populated.
  grpc::Status SendEvent(
      grpc::ServerContext* context,
      const profiler::proto::EventProfilerData* request,
      profiler::proto::EmptyEventResponse* response) override;

 private:
  EventCache& cache_;
};

}  // namespace profiler

#endif  // PERFD_EVENT_INTERNAL_EVENT_SERVICE_H_
