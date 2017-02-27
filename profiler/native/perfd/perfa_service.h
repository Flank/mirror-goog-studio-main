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
#ifndef PERFD_PERFA_SERVICE_H_
#define PERFD_PERFA_SERVICE_H_

#include <grpc++/grpc++.h>

#include "proto/perfa_service.grpc.pb.h"
#include "utils/clock.h"

#include <unordered_map>

namespace profiler {

class PerfaServiceImpl : public proto::PerfaService::Service {
 public:
  explicit PerfaServiceImpl(
      const Clock& clock,
      std::unordered_map<int32_t, int64_t>* heartbeat_timestamp_map)
      : clock_(clock), heartbeat_timestamp_map_(*heartbeat_timestamp_map) {}

  grpc::Status HeartBeat(grpc::ServerContext* context,
                         const proto::CommonData* data,
                         proto::HeartBeatResponse* response) override;

 private:
  const Clock& clock_;
  // used for marking the last time this service receives a ping from perfa.
  std::unordered_map<int32_t, int64_t>& heartbeat_timestamp_map_;
};

}  // namespace profiler

#endif  // PERFD_PERFA_SERVICE_H_
