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
#ifndef PERFD_NETWORK_CONNECTION_SAMPLER_H
#define PERFD_NETWORK_CONNECTION_SAMPLER_H

#include "perfd/network/network_sampler.h"

#include <string>
#include <vector>

namespace profiler {

// Data collector of open connection information. For example, it can
// collect the number of both tcp and udp open connections.
class ConnectionSampler final : public NetworkSampler {
 public:
  ConnectionSampler(const std::string &uid,
                    const std::vector<std::string> &files)
      : files_(files), uid_(atoi(uid.c_str())) {}

  // Read system file to get the number of open connections, and store data in
  // given {@code NetworkProfilerData}.
  void GetData(profiler::proto::NetworkProfilerData *data) override;

 private:
  // Returns open connection number that is read from a given file.
  int ReadConnectionNumber(const std::string &file, char *buffer);

  // Index indicates the location of app uid(unique id), in the connection
  // system files. One open connection is listed as a line in file. Tokens
  // are joined by whitespace in a line. For example, a connection line is
  // "01: 001:002:123 001:002:001 01 02 03 04 20555...".
  // Index of Uid token "20555" is 7.
  static const int kUidTokenIndex = 7;

  // Buffer length 4096 is the maximum line length of formatted proc files.
  // An example in opensource code is platform/external/toybox/netstat.c
  // and buffer is defined in the header file toys.h.
  static const int kLineBufferSize = 4096;

  // Max size of ipv4/ipv6 address string. Address is parsed from proc file
  // like 0000000000000000FFFF00000100007F as ipv6 address.
  static const int kAddressStringSize = 33;

  // List of files containing open connection data; for example /proc/net/tcp6.
  // Those files contain multiple apps' information.
  const std::vector<std::string> files_;

  // Unsigned integer that is app uid for parsing file to get connections.
  const uint32_t uid_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_CONNECTION_SAMPLER_H
