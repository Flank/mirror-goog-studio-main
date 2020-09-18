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

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <climits>
#include <cstdint>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::ProcessCountersParameters
    ProcessCountersParameters;
typedef proto::ProcessCountersResult ProcessCountersResult;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/tank.trace");

const long TANK_PROCESS_PID = 9796;

struct counter_accumulator {
  long int occurrences = 0;
  int64_t first_entry_ts = INT64_MAX;
  int64_t last_entry_ts = INT64_MIN;
  double min_value = DBL_MAX;
  double max_value = -DBL_MAX;
};

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

TEST(CountersRequestHandlerTest, PopulateCounters) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = CountersRequestHandler(tp.get());

  ProcessCountersParameters params_proto;
  params_proto.set_process_id(TANK_PROCESS_PID);

  ProcessCountersResult result;
  handler.PopulateCounters(params_proto, &result);

  EXPECT_EQ(result.process_id(), TANK_PROCESS_PID);
  EXPECT_EQ(result.counter_size(), 11);

  std::unordered_map<std::string, counter_accumulator> counter_map;

  for (auto counter : result.counter()) {
    counter_accumulator acc;
    for (auto entry : counter.value()) {
      acc.occurrences++;

      acc.first_entry_ts =
          std::min(acc.first_entry_ts, entry.timestamp_nanoseconds());
      acc.last_entry_ts =
          std::max(acc.last_entry_ts, entry.timestamp_nanoseconds());

      acc.min_value = std::min(acc.min_value, entry.value());
      acc.max_value = std::max(acc.max_value, entry.value());
    }
    counter_map[counter.name()] = acc;
  }

  EXPECT_EQ(counter_map["mem.rss"].occurrences, 48);
  EXPECT_EQ(counter_map["mem.rss"].first_entry_ts, 962666095076);
  EXPECT_EQ(counter_map["mem.rss"].last_entry_ts, 1009667965071);
  EXPECT_EQ(counter_map["mem.rss"].min_value, 72224768.0);
  EXPECT_EQ(counter_map["mem.rss"].max_value, 374648832.0);

  EXPECT_EQ(counter_map["mem.virt"].occurrences, 48);
  EXPECT_EQ(counter_map["mem.virt"].first_entry_ts, 962666095076);
  EXPECT_EQ(counter_map["mem.virt"].last_entry_ts, 1009667965071);
  EXPECT_EQ(counter_map["mem.virt"].min_value, 1211494400.0);
  EXPECT_EQ(counter_map["mem.virt"].max_value, 3200487424.0);

  EXPECT_EQ(counter_map["oom_score_adj"].occurrences, 48);
  EXPECT_EQ(counter_map["oom_score_adj"].first_entry_ts, 962666095076);
  EXPECT_EQ(counter_map["oom_score_adj"].last_entry_ts, 1009667965071);
  EXPECT_EQ(counter_map["oom_score_adj"].min_value, 0.0);
  EXPECT_EQ(counter_map["oom_score_adj"].max_value, 0.0);

  std::string player_activity =
      "aq:pending:com.google.android.tanks/"
      "com.unity3d.player.UnityPlayerActivity";
  EXPECT_EQ(counter_map[player_activity].occurrences, 34);
  EXPECT_EQ(counter_map[player_activity].first_entry_ts, 990062118482);
  EXPECT_EQ(counter_map[player_activity].last_entry_ts, 998726603147);
  EXPECT_EQ(counter_map[player_activity].min_value, 0.0);
  EXPECT_EQ(counter_map[player_activity].max_value, 1.0);
}

TEST(CountersRequestHandlerTest, PopulateCountersNoProcessId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = CountersRequestHandler(tp.get());

  ProcessCountersParameters params_proto;

  ProcessCountersResult result;
  handler.PopulateCounters(params_proto, &result);

  EXPECT_EQ(result.process_id(), 0);
  EXPECT_EQ(result.counter_size(), 0);
}

TEST(CountersRequestHandlerTest, PopulateCpuCoreCounters) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = CountersRequestHandler(tp.get());

  proto::QueryParameters::CpuCoreCountersParameters params_proto;
  proto::CpuCoreCountersResult result;
  handler.PopulateCpuCoreCounters(params_proto, &result);

  EXPECT_EQ(result.num_cores(), 8);
  EXPECT_EQ(result.counters_per_core_size(), 8);

  counter_accumulator expected_freq[] = {
      counter_accumulator{2070, 949125196591, 1009905239625, 576000.0,
                          1766400.0},
      counter_accumulator{2070, 949125392425, 1009905269261, 576000.0,
                          1766400.0},
      counter_accumulator{2070, 949125398727, 1009905275771, 576000.0,
                          1766400.0},
      counter_accumulator{2070, 949125401435, 1009905281552, 576000.0,
                          1766400.0},
      counter_accumulator{1122, 949125219248, 1008770240346, 825600.0,
                          2803200.0},
      counter_accumulator{1122, 949125411539, 1008771915658, 825600.0,
                          2803200.0},
      counter_accumulator{1122, 949125414352, 1008771921179, 825600.0,
                          2803200.0},
      counter_accumulator{1122, 949125416852, 1008771922273, 825600.0,
                          2803200.0},
  };

  for (size_t i = 0; i < result.counters_per_core_size(); ++i) {
    std::unordered_map<std::string, counter_accumulator> counter_map;
    for (auto counter : result.counters_per_core(i).counter()) {
      counter_accumulator acc;
      for (auto entry : counter.value()) {
        acc.occurrences++;

        acc.first_entry_ts =
            std::min(acc.first_entry_ts, entry.timestamp_nanoseconds());
        acc.last_entry_ts =
            std::max(acc.last_entry_ts, entry.timestamp_nanoseconds());

        acc.min_value = std::min(acc.min_value, entry.value());
        acc.max_value = std::max(acc.max_value, entry.value());
      }
      counter_map[counter.name()] = acc;
    }

    EXPECT_EQ(counter_map["cpufreq"].occurrences, expected_freq[i].occurrences);
    EXPECT_EQ(counter_map["cpufreq"].first_entry_ts,
              expected_freq[i].first_entry_ts);
    EXPECT_EQ(counter_map["cpufreq"].last_entry_ts,
              expected_freq[i].last_entry_ts);
    EXPECT_EQ(counter_map["cpufreq"].min_value, expected_freq[i].min_value);
    EXPECT_EQ(counter_map["cpufreq"].max_value, expected_freq[i].max_value);
  }
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
