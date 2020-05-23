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

#include "trace_processor_service.h"

#include <grpc++/grpc++.h>

#include "counters/counters_request_handler.h"
#include "memory/memory_request_handler.h"
#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"
#include "process_metadata/process_metadata_request_handler.h"
#include "scheduling/scheduling_request_handler.h"
#include "trace_events/trace_events_request_handler.h"

using ::perfetto::trace_processor::Config;
using ::perfetto::trace_processor::ReadTrace;
using ::perfetto::trace_processor::TraceProcessor;

namespace profiler {
namespace perfetto {

using proto::QueryParameters;

grpc::Status TraceProcessorServiceImpl::LoadTrace(
    grpc::ServerContext* context, const proto::LoadTraceRequest* request,
    proto::LoadTraceResponse* response) {
  auto trace_id = request->trace_id();
  if (trace_id == 0) {
    response->set_ok(false);
    response->set_error("Invalid Trace ID.");
    return grpc::Status::OK;
  }

  auto trace_path = request->trace_path();
  if (trace_path.empty()) {
    response->set_ok(false);
    response->set_error("Empty Trace Path.");
    return grpc::Status::OK;
  }

  Config config;
  // Avoid filling the RAW table with ftrace events, as we will not want to
  // export the trace back into systrace format. This allows TP to save a good
  // chunk of memory.
  config.ingest_ftrace_in_raw_table = false;

  tp_ = TraceProcessor::CreateInstance(config);

  std::cout << "Loading trace (" << trace_id << ") from: " << trace_path
            << std::endl;

  auto read_status = ReadTrace(tp_.get(), trace_path.c_str(), {});

  response->set_ok(read_status.ok());
  if (!read_status.ok()) {
    response->set_error(read_status.message());
  }

  // Default query, not restricted to any process.
  proto::QueryParameters::ProcessMetadataParameters metadata_params;
  ProcessMetadataRequestHandler handler(tp_.get());
  handler.PopulateMetadata(metadata_params,
                           response->mutable_process_metadata());

  return grpc::Status::OK;
}

grpc::Status TraceProcessorServiceImpl::QueryBatch(
    grpc::ServerContext* context, const proto::QueryBatchRequest* batch_request,
    proto::QueryBatchResponse* batch_response) {
  for (auto& request : batch_request->query()) {
    // Keep in the same order as the proto file.
    switch (request.query_case()) {
      case QueryParameters::kProcessMetadataRequest: {
        ProcessMetadataRequestHandler handler(tp_.get());
        handler.PopulateMetadata(
            request.process_metadata_request(),
            batch_response->add_result()->mutable_process_metadata_result());
      } break;
      case QueryParameters::kTraceEventsRequest: {
        TraceEventsRequestHandler handler(tp_.get());
        handler.PopulateTraceEvents(
            request.trace_events_request(),
            batch_response->add_result()->mutable_trace_events_result());
      } break;
      case QueryParameters::kSchedRequest: {
        SchedulingRequestHandler handler(tp_.get());
        handler.PopulateEvents(
            request.sched_request(),
            batch_response->add_result()->mutable_sched_result());
      } break;
      case QueryParameters::kMemoryRequest: {
        MemoryRequestHandler handler(tp_.get());
        handler.PopulateEvents(
            batch_response->add_result()->mutable_memory_events());
      } break;
      case QueryParameters::kCountersRequest: {
        CountersRequestHandler handler(tp_.get());
        handler.PopulateCounters(
            request.counters_request(),
            batch_response->add_result()->mutable_counters_result());
      } break;
    }
  }
  return grpc::Status::OK;
}

}  // namespace perfetto
}  // namespace profiler
