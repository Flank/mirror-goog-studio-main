/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef TRACE_PROCESSOR_SERVICE_H_
#define TRACE_PROCESSOR_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfetto/trace_processor/trace_processor.h"
#include "trace_processor_service.grpc.pb.h"
#include "trace_processor_service.pb.h"

using perfetto::trace_processor::TraceProcessor;
using std::string;

namespace profiler {
namespace perfetto {

class TraceProcessorServiceImpl final
    : public proto::TraceProcessorService::Service {
 public:
  grpc::Status LoadTrace(grpc::ServerContext* context,
                         const proto::LoadTraceRequest* request,
                         proto::LoadTraceResponse* response) override;

 private:
  int loaded_trace_id_ = 0;
  TraceProcessor* tp_ = nullptr;

  void LoadAllProcessMetadata(proto::ProcessMetadataResult* metadata);
};

}  // namespace perfetto
}  // namespace profiler

#endif  // TRACE_PROCESSOR_SERVICE_H_
