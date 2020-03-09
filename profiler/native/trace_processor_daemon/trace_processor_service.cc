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

#include "memory/memory_request_handler.h"
#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

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

  LoadAllProcessMetadata(response->mutable_process_metadata());

  return grpc::Status::OK;
}

void TraceProcessorServiceImpl::LoadAllProcessMetadata(
    proto::ProcessMetadataResult* metadata) {
  std::unordered_map<long, proto::ProcessMetadataResult::ProcessMetadata*>
      process_map;

  auto it_process = tp_->ExecuteQuery(
      "SELECT upid, pid, name FROM process WHERE pid != 0 ORDER BY upid ASC");
  while (it_process.Next()) {
    auto process_proto = metadata->add_process();

    auto upid = it_process.Get(0).long_value;
    process_proto->set_internal_id(upid);

    auto pid = it_process.Get(1).long_value;
    process_proto->set_id(pid);

    auto name_sql_value = it_process.Get(2);
    auto name = name_sql_value.is_null() ? "" : name_sql_value.string_value;
    process_proto->set_name(name);

    process_map[upid] = process_proto;
  }

  auto it_thread = tp_->ExecuteQuery(
      "SELECT upid, utid, tid, name FROM thread ORDER BY upid ASC, utid ASC");
  while (it_thread.Next()) {
    auto upid = it_thread.Get(0).long_value;
    auto utid = it_thread.Get(1).long_value;
    if (process_map.find(upid) == process_map.end()) {
      // We got a thread that we don't know which process it belongs to.
      continue;
    }

    auto process_proto = process_map[upid];
    auto thread_proto = process_proto->add_thread();

    thread_proto->set_internal_id(utid);

    auto tid = it_thread.Get(2).long_value;
    thread_proto->set_id(tid);

    auto name_sql_value = it_thread.Get(3);
    auto name = name_sql_value.is_null() ? "" : name_sql_value.string_value;
    thread_proto->set_name(name);
  }
}

grpc::Status TraceProcessorServiceImpl::QueryBatch(
    grpc::ServerContext* context, const proto::QueryBatchRequest* batch_request,
    proto::QueryBatchResponse* batch_response) {
  for (auto& request : batch_request->query()) {
    switch (request.query_case()) {
      case QueryParameters::kMemoryRequest:
        MemoryRequestHandler handler(tp_.get());
        handler.PopulateEvents(
            batch_response->add_result()->mutable_memory_events());
        break;
    }
  }
  return grpc::Status::OK;
}

}  // namespace perfetto
}  // namespace profiler
