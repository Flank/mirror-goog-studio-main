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
#include "net_stats_file_reader.h"

#include "utils/file_reader.h"
#include "utils/tokenizer.h"

namespace profiler {

void NetStatsFileReader::Refresh() {
  bytes_tx_ = 0;
  bytes_rx_ = 0;

  std::vector<std::string> lines;
  FileReader::Read(file_, &lines);

  for (const std::string &line : lines) {
    Tokenizer t(line);
    // Line, broken into tokens, with tokens we care about |highlighted|:
    // idx iface acct_tag_hex |uid| cnt_set |rx_bytes| rx_packets |tx_bytes|
    // Currently, we are not only sampling the user's traffic but also the
    // bytes sent between agent <-> perfd, which to the user is noise. Here,
    // we ignore the bytes sent on the loopback device to avoid counting such
    // traffic. We agree as of right now that, users care about traffic from
    // outside much more than inter-process traffic.
    std::string str;
    if (t.SkipTokens(1) && t.GetNextToken(&str) && str != "lo" &&
        t.SkipTokens(1) && t.GetNextToken(&str) && str == uid_) {
      std::string rx_str;
      std::string tx_str;
      if (t.SkipTokens(1) && t.GetNextToken(&rx_str) && t.SkipTokens(1) &&
          t.GetNextToken(&tx_str)) {
        // TODO: Use std::stoll() after we use libc++, and remove '.c_str()'.
        bytes_tx_ += atol(tx_str.c_str());
        bytes_rx_ += atol(rx_str.c_str());
      }
    }
  }
}

}  // namespace profiler
