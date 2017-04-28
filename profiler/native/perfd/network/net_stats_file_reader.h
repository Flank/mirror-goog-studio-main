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

#include "perfd/network/network_sampler.h"

#include <string>

namespace profiler {

// Class which, on demand, parses a file formatted like the one in
// "/proc/net/xt_qtaguid/stats", exposing interesting data contained within.
//
// Note that the stats file is expected to change over time, so the user of this
// class should call |Refresh| before checking the latest values.
class NetStatsFileReader final {
 public:
  NetStatsFileReader(const std::string &uid, const std::string &file) :
    uid_(atoi(uid.c_str())), file_(file) {}

  // Reparse the target stats file, updating the local copy of data values read
  // from it
  void Refresh();

  // Sent (transmitted) bytes since device boot
  int64_t bytes_tx() { return bytes_tx_; }

  // Received bytes since device boot
  int64_t bytes_rx() { return bytes_rx_; }

 private:
  // Unsigned integer that is app uid for parsing file to get traffic bytes.
  const uint32_t uid_;
  const std::string file_;

  int64_t bytes_tx_{0};
  int64_t bytes_rx_{0};
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NET_STATS_FILE_READER_H_
