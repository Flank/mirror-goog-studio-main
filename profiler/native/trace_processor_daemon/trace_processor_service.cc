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
using proto::QueryResult;

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

  // Since tp_ is a unique_ptr, it will automatically release/delete the
  // previously loaded trace.
  tp_ = TraceProcessor::CreateInstance(config);

  std::cout << "Loading trace (" << trace_id << ") from: " << trace_path
            << std::endl;

  auto read_status = ReadTrace(tp_.get(), trace_path.c_str(), {});

  response->set_ok(read_status.ok());
  if (!read_status.ok()) {
    response->set_error(read_status.message());

    // Reset tp_ to release the loaded trace.
    tp_.reset(nullptr);
    loaded_trace_id = 0;

    return grpc::Status::OK;
  }

  loaded_trace_id = trace_id;
  return grpc::Status::OK;
}

grpc::Status TraceProcessorServiceImpl::QueryBatch(
    grpc::ServerContext* context, const proto::QueryBatchRequest* batch_request,
    proto::QueryBatchResponse* batch_response) {
  for (auto& request : batch_request->query()) {
    auto query_result = batch_response->add_result();

    auto request_trace_id = request.trace_id();

    // Guard against "last loaded trace" when we have no loaded trace.
    if (tp_.get() == nullptr) {
      query_result->set_ok(false);
      query_result->set_failure_reason(QueryResult::TRACE_NOT_FOUND);
      query_result->set_error("No trace loaded.");
      continue;
    }

    if (request_trace_id != 0 && request_trace_id != loaded_trace_id) {
      query_result->set_ok(false);
      query_result->set_failure_reason(QueryResult::TRACE_NOT_FOUND);
      query_result->set_error("Unknown trace " +
                              std::to_string(request_trace_id));
      continue;
    }

    // Keep in the same order as the proto file.
    switch (request.query_case()) {
      case QueryParameters::kProcessMetadataRequest: {
        ProcessMetadataRequestHandler handler(tp_.get());
        handler.PopulateMetadata(
            request.process_metadata_request(),
            query_result->mutable_process_metadata_result());
      } break;
      case QueryParameters::kTraceEventsRequest: {
        TraceEventsRequestHandler handler(tp_.get());
        handler.PopulateTraceEvents(
            request.trace_events_request(),
            query_result->mutable_trace_events_result());
      } break;
      case QueryParameters::kSchedRequest: {
        SchedulingRequestHandler handler(tp_.get());
        handler.PopulateEvents(request.sched_request(),
                               query_result->mutable_sched_result());
      } break;
      case QueryParameters::kMemoryRequest: {
        MemoryRequestHandler handler(tp_.get());
        handler.PopulateEvents(query_result->mutable_memory_events());
      } break;
      case QueryParameters::kProcessCountersRequest: {
        CountersRequestHandler handler(tp_.get());
        handler.PopulateCounters(
            request.process_counters_request(),
            query_result->mutable_process_counters_result());
      } break;
      case QueryParameters::kCpuCoreCountersRequest: {
        CountersRequestHandler handler(tp_.get());
        handler.PopulateCpuCoreCounters(
            request.cpu_core_counters_request(),
            query_result->mutable_cpu_core_counters_result());
      } break;
      case QueryParameters::QUERY_NOT_SET:
        // Do nothing.
        break;
    }
    query_result->set_ok(true);
  }
  return grpc::Status::OK;
}

}  // namespace perfetto
}  // namespace profiler
