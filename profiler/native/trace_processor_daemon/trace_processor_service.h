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
#include <shared_mutex>

#include "perfetto/trace_processor/trace_processor.h"
#include "proto/trace_processor_service.grpc.pb.h"
#include "proto/trace_processor_service.pb.h"

namespace profiler {
namespace perfetto {

class TraceProcessorServiceImpl final
    : public proto::TraceProcessorService::Service {
 public:
  TraceProcessorServiceImpl() {}
  TraceProcessorServiceImpl(const std::string& llvm_path)
      : llvm_path_(llvm_path) {}
  grpc::Status LoadTrace(grpc::ServerContext* context,
                         const proto::LoadTraceRequest* request,
                         proto::LoadTraceResponse* response) override;
  grpc::Status QueryBatch(grpc::ServerContext* context,
                          const proto::QueryBatchRequest* request,
                          proto::QueryBatchResponse* response) override;

 private:
  // Mutex to control access to the loaded trace, to prevent a trace to being
  // unloaded while a batch query is still being run against it for example.
  std::shared_mutex tp_mutex;
  std::unique_ptr<::perfetto::trace_processor::TraceProcessor> tp_;
  int64_t loaded_trace_id = 0;
  std::string llvm_path_;

  void LoadAllProcessMetadata(proto::ProcessMetadataResult* metadata);
};

}  // namespace perfetto
}  // namespace profiler

#endif  // TRACE_PROCESSOR_SERVICE_H_
