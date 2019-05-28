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
#ifndef PERFD_CPU_INTERNAL_CPU_SERVICE_H_
#define PERFD_CPU_INTERNAL_CPU_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/cpu/trace_manager.h"
#include "utils/file_cache.h"

#include "proto/cpu.grpc.pb.h"
#include "proto/internal_cpu.grpc.pb.h"

namespace profiler {

class InternalCpuServiceImpl final
    : public profiler::proto::InternalCpuService::Service {
 public:
  explicit InternalCpuServiceImpl(TraceManager* trace_manager,
                                  FileCache* file_cache)
      : trace_manager_(trace_manager), file_cache_(file_cache) {}

  grpc::Status SendTraceEvent(
      grpc::ServerContext* context,
      const profiler::proto::CpuTraceOperationRequest* request,
      profiler::proto::CpuTraceOperationResponse* response) override;

 private:
  TraceManager* trace_manager_;
  FileCache* file_cache_;
};

}  // namespace profiler

#endif  // PERFD_CPU_INTERNAL_CPU_SERVICE_H_
