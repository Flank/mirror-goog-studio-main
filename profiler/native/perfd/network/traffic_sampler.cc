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
#include "traffic_sampler.h"

#include "utils/file_reader.h"
#include "utils/tokenizer.h"

#include <cstdlib>

namespace profiler {

void TrafficSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  int64_t bytes_sent = 0;
  int64_t bytes_received = 0;

  std::vector<std::string> lines;
  FileReader::Read(file_, &lines);

  for (const std::string &line : lines) {
    Tokenizer t(line);
    // Line, broken into tokens, with tokens we care about |highlighted|:
    // idx iface acct_tag_hex |uid| cnt_set |rx_bytes| rx_packets |tx_bytes|
    std::string uid_str;
    if (t.EatTokens(3) && t.GetNextToken(&uid_str) && uid_str == uid_) {
      std::string rx_str;
      std::string tx_str;
      if (t.EatTokens(1) && t.GetNextToken(&rx_str) && t.EatTokens(1) &&
          t.GetNextToken(&tx_str)) {
        // TODO: Use std::stoll() after we use libc++, and remove '.c_str()'.
        bytes_sent += atol(tx_str.c_str());
        bytes_received += atol(rx_str.c_str());
      }
    }
  }

  profiler::proto::TrafficData *traffic_data = data->mutable_traffic_data();
  traffic_data->set_bytes_sent(bytes_sent);
  traffic_data->set_bytes_received(bytes_received);
}

}  // namespace profiler
