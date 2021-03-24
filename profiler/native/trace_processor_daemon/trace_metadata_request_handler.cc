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

#include "trace_metadata_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::TraceMetadataResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::TraceMetadataParameters TraceMetadataParameters;

void TraceMetadataRequestHandler::PopulateTraceMetadata(
    const TraceMetadataParameters& params, TraceMetadataResult* result) {
  if (result == nullptr) {
    return;
  }

  std::string name_query = params.name().empty() ? "%" : params.name();
  std::string type_query = params.type().empty() ? "%" : params.type();
  std::string query_string =
    "SELECT name, key_type, int_value, str_value "
    "FROM metadata "
    "WHERE name like '" +
    name_query +
    "' AND type like '" +
    type_query +
    "'";
  auto it = tp_->ExecuteQuery(query_string);
  while (it.Next()) {
      auto row = result->add_metadata_row();
      row->set_name(it.Get(0).string_value);
      row->set_key_type(it.Get(1).string_value);
      if (!it.Get(2).is_null()) {
          row->set_int64_value(it.Get(2).long_value);
      } else if (!it.Get(3).is_null()) {
          row->set_string_value(it.Get(3).string_value);
      }
  }
}
