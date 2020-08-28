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

#include "counters_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::Counter;
using profiler::perfetto::proto::CountersResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::CountersParameters CountersParameters;

void CountersRequestHandler::PopulateCounters(CountersParameters params,
                                              CountersResult* result) {
  if (result == nullptr || params.process_id() == 0) {
    return;
  }

  result->set_process_id(params.process_id());

  std::unordered_map<std::string, Counter*> counters_map;

  std::string query_string =
      "SELECT process_counter_track.name, counter.ts, counter.value "
      "FROM counter "
      "     INNER JOIN process_counter_track "
      "         ON process_counter_track.id = counter.track_id "
      "     INNER JOIN process using(upid) "
      "WHERE process.pid = " +
      std::to_string(params.process_id()) +
      " "
      "ORDER BY process_counter_track.name ASC, counter.ts ASC;";

  auto it_counters = tp_->ExecuteQuery(query_string);
  while (it_counters.Next()) {
    auto counter_name_sql_value = it_counters.Get(0);
    if (counter_name_sql_value.is_null()) {
      continue;
    }

    auto counter_name = counter_name_sql_value.string_value;

    if (counters_map.find(counter_name) == counters_map.end()) {
      counters_map[counter_name] = result->add_counter();
      counters_map[counter_name]->set_name(counter_name);
    }

    auto counter_value_proto = counters_map[counter_name]->add_value();

    auto ts_nanos = it_counters.Get(1).long_value;
    counter_value_proto->set_timestamp_nanoseconds(ts_nanos);

    auto value = it_counters.Get(2).double_value;
    counter_value_proto->set_value(value);
  }
}
