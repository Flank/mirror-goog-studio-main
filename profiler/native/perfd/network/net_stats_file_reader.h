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
#ifndef PERFD_NETWORK_NET_STATS_FILE_READER_H_
#define PERFD_NETWORK_NET_STATS_FILE_READER_H_

#include <string>
#include <unordered_map>

namespace profiler {

// Class which, on demand, parses a file formatted like the one in
// "/proc/net/xt_qtaguid/stats", exposing interesting data contained within.
//
// Note that the stats file is expected to change over time, so the user of this
// class should call |Refresh| before checking the latest values.
class NetStatsFileReader final {
 public:
  NetStatsFileReader(const std::string &file) : file_(file) {}
  // Reparse the target stats file, updating the local copy of data values read
  // from it
  void Refresh();
  // Sent (transmitted) bytes since device boot of a specific app.
  uint64_t bytes_tx(uint32_t id);
  // Received bytes since device boot of a specific app.
  uint64_t bytes_rx(uint32_t id);

 private:
  const std::string file_;
  // Mapping of app uid to the app's sent/received bytes. After |Refresh|
  // is called, all app's sent/received bytes data are stored in the map.
  // If an app has sent/received bytes since device boot, it has a map entry.
  std::unordered_map<uint32_t, uint64_t> bytes_tx_;
  std::unordered_map<uint32_t, uint64_t> bytes_rx_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NET_STATS_FILE_READER_H_
