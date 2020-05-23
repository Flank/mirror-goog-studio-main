/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "process_metadata_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::ProcessMetadataResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::ProcessMetadataParameters ProcessMetadataParameters;

void ProcessMetadataRequestHandler::PopulateMetadata(
    ProcessMetadataParameters params, ProcessMetadataResult* result) {
  if (result == nullptr) {
    return;
  }

  std::string query_string;
  if (params.process_id() > 0) {
    query_string =
        "SELECT upid, pid, process.name, utid, tid, thread.name "
        "FROM process INNER JOIN thread using(upid) "
        "WHERE pid = " +
        std::to_string(params.process_id()) +
        " "
        "ORDER BY upid ASC, utid ASC";
  } else {
    query_string =
        "SELECT upid, pid, process.name, utid, tid, thread.name "
        "FROM process INNER JOIN thread using(upid) "
        "ORDER BY upid ASC, utid ASC";
  }

  std::unordered_map<long, proto::ProcessMetadataResult::ProcessMetadata*>
      process_map;

  auto it = tp_->ExecuteQuery(query_string);
  while (it.Next()) {
    auto upid = it.Get(0).long_value;

    if (process_map.find(upid) == process_map.end()) {
      auto process_proto = result->add_process();

      process_proto->set_internal_id(upid);

      auto pid = it.Get(1).long_value;
      process_proto->set_id(pid);

      auto name_sql_value = it.Get(2);
      auto name = name_sql_value.is_null() ? "" : name_sql_value.string_value;
      process_proto->set_name(name);

      process_map[upid] = process_proto;
    }

    auto thread_proto = process_map[upid]->add_thread();

    auto utid = it.Get(3).long_value;
    thread_proto->set_internal_id(utid);

    auto tid = it.Get(4).long_value;
    thread_proto->set_id(tid);

    auto name_sql_value = it.Get(5);
    auto name = name_sql_value.is_null() ? "" : name_sql_value.string_value;
    thread_proto->set_name(name);
  }
}
