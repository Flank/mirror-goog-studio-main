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

#include <inttypes.h>

namespace profiler {

void NetStatsFileReader::Refresh() {
  FILE *fp = fopen(file_.c_str(), "r");
  if (fp == NULL) {
    return;
  }

  bytes_tx_ = 0;
  bytes_rx_ = 0;

  // Buffer length 512 is the maximum line length of formatted proc stat file.
  // An example in opensource code is
  // platform/frameworks/base/core/jni/android_net_TrafficStats.cpp
  char buffer[512];
  char iface[64];

  uint32_t idx, uid, set;
  uint64_t tag, rx_bytes, rx_packets, tx_bytes;

  // Line, broken into tokens, with tokens we care about |highlighted|:
  // idx iface acct_tag_hex |uid| cnt_set |rx_bytes| rx_packets |tx_bytes|
  // Currently, we are not only sampling the user's traffic but also the
  // bytes sent between agent <-> perfd, which to the user is noise. Here,
  // we ignore the bytes sent on the loopback device to avoid counting such
  // traffic. We agree as of right now that, users care about traffic from
  // outside much more than inter-process traffic.
  while(fgets(buffer, sizeof(buffer), fp) != NULL) {
    if (sscanf(buffer,
               "%" SCNu32 " %31s 0x%" SCNx64 " %u %u %" SCNu64 " %" SCNu64 " %"
               SCNu64, &idx, iface, &tag, &uid, &set, &rx_bytes, &rx_packets,
               &tx_bytes) == 8 && uid_ == uid && strcmp("lo", iface) != 0) {
      bytes_tx_ += tx_bytes;
      bytes_rx_ += rx_bytes;
    }
  }
  fclose(fp);
}

}  // namespace profiler
