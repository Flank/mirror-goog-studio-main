/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "perfd/network/net_stats_file_reader.h"
#include "test/utils.h"

#include <gtest/gtest.h>

using profiler::NetStatsFileReader;
using profiler::TestUtils;

TEST(NetStatsFileReader, NewReaderReturnsZeros) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_uid_matched_single_entry.txt"));
  NetStatsFileReader statsReader(file_name);
  EXPECT_EQ(0, statsReader.bytes_rx(12345));
  EXPECT_EQ(0, statsReader.bytes_tx(12345));
  statsReader.Refresh();
  EXPECT_NE(0, statsReader.bytes_rx(12345));
  EXPECT_NE(0, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, OutputIsFromSingleLineEntry) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_uid_matched_single_entry.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(1111, statsReader.bytes_rx(12345));
  EXPECT_EQ(2222, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, OutputIsSumOfMultiLineEntries) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_uid_matched_multiple_entries.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(3333, statsReader.bytes_rx(12345));
  EXPECT_EQ(6666, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, OutputIsZeroAsUnmatchUidEntryIsFilteredOut) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_uid_unmatched.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(0, statsReader.bytes_rx(12345));
  EXPECT_EQ(0, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, OutputFiltersOutLoopbackTraffic) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_filter_out_loopback_traffic.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(0, statsReader.bytes_rx(12345));
  EXPECT_EQ(0, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, OutputFiltersOutLoopbackAndKeepNonLoopbackTraffic) {
  std::string file_name(
    TestUtils::getNetworkTestData(
        "traffic_having_loopback_and_nonloopback_traffic.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(2222, statsReader.bytes_rx(12345));
  EXPECT_EQ(3333, statsReader.bytes_tx(12345));
}

TEST(NetStatsFileReader, ThreeUidsData) {
  std::string file_name(
    TestUtils::getNetworkTestData("traffic_three_uids_data.txt"));
  NetStatsFileReader statsReader(file_name);
  statsReader.Refresh();
  EXPECT_EQ(1110, statsReader.bytes_rx(12340));
  EXPECT_EQ(2220, statsReader.bytes_tx(12340));
  EXPECT_EQ(1111, statsReader.bytes_rx(12341));
  EXPECT_EQ(2221, statsReader.bytes_tx(12341));
  EXPECT_EQ(1112, statsReader.bytes_rx(12342));
  EXPECT_EQ(2222, statsReader.bytes_tx(12342));
}
