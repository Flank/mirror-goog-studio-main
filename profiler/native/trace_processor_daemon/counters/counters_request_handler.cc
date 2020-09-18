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
using profiler::perfetto::proto::CpuCoreCountersResult;
using profiler::perfetto::proto::ProcessCountersResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::CpuCoreCountersParameters CpuCoreCountersParameters;
typedef QueryParameters::ProcessCountersParameters ProcessCountersParameters;

void CountersRequestHandler::PopulateCounters(ProcessCountersParameters params,
                                              ProcessCountersResult* result) {
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

void CountersRequestHandler::PopulateCpuCoreCounters(
    CpuCoreCountersParameters params, CpuCoreCountersResult* result) {
  if (result == nullptr) {
    return;
  }

  // Here we query in the CPU counter table for the highest cpu core ID
  // and add 1 to get the core count.
  auto it_cpu_count = tp_->ExecuteQuery(
      "SELECT cpu FROM cpu_counter_track ORDER BY cpu DESC LIMIT 1;");
  if (it_cpu_count.Next()) {
    auto max_core = it_cpu_count.Get(0).long_value;
    result->set_num_cores(max_core + 1);
  }

  if (result->num_cores() == 0) {
    std::cerr << "CpuCoreCountersResult with 0 cpu cores." << std::endl;
    return;
  }

  // Pre-populate [num_cores] CpuCounterResult(s).
  for (int32_t i = 0; i < result->num_cores(); ++i) {
    result->add_counters_per_core()->set_cpu(i);
  }

  std::string query_string =
      "SELECT t.cpu, t.name, c.ts, c.value "
      "FROM counter c INNER JOIN cpu_counter_track t "
      "     ON t.id = c.track_id "
      "WHERE IFNULL(t.name, '') != '' "
      "ORDER BY t.cpu ASC, t.name ASC, c.ts ASC;";

  int64_t current_cpu = 0;
  std::unordered_map<std::string, Counter*> counters_map;
  auto it_counters = tp_->ExecuteQuery(query_string);
  // Query result:
  // | cpu | name   | ts | value |
  // |   0 | cpufreq|  1 | 10000 |
  // |   0 | cpufreq|  2 | 10000 |
  // |   1 | cpufreq|  3 | 20000 |
  while (it_counters.Next()) {
    auto cpu_value = it_counters.Get(0).long_value;
    if (current_cpu != cpu_value) {
      // Move on to the next core.
      current_cpu = cpu_value;
      counters_map.clear();
    }

    // No need to check for NULL since the query already does.
    auto counter_name = it_counters.Get(1).string_value;
    if (counters_map.find(counter_name) == counters_map.end()) {
      counters_map[counter_name] =
          result->mutable_counters_per_core(cpu_value)->add_counter();
      counters_map[counter_name]->set_name(counter_name);
    }

    auto counter_value_proto = counters_map[counter_name]->add_value();

    auto ts_nanos = it_counters.Get(2).long_value;
    counter_value_proto->set_timestamp_nanoseconds(ts_nanos);

    auto value = it_counters.Get(3).double_value;
    counter_value_proto->set_value(value);
  }
}
