/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "io_network_type_provider.h"

#include <arpa/inet.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <unistd.h>

namespace profiler {

proto::ConnectivityData::NetworkType
IoNetworkTypeProvider::GetDefaultNetworkType() {
  proto::ConnectivityData::NetworkType type = proto::ConnectivityData::INVALID;
  int sockfd = socket(AF_INET, SOCK_DGRAM, 0);
  if(sockfd < 0) {
    return type;
  }

  // Query available interfaces.
  char buf[1024];
  struct ifconf ifc;
  ifc.ifc_len = sizeof(buf);
  ifc.ifc_buf = buf;

  struct ifreq *ifr = nullptr;
  if(ioctl(sockfd, SIOCGIFCONF, &ifc) >= 0) {
    ifr = ifc.ifc_req;
  }
  int interfaces = ifr != nullptr ? ifc.ifc_len / sizeof(struct ifreq) : 0;
  for (int i = 0; i < interfaces; i++) {
    auto name = ifr[i].ifr_name;
    if (name == nullptr || strncmp(name, "lo", 2) == 0) {
      continue;
    }
    if (strncmp(name, "wlan", 4) == 0) {
      type = proto::ConnectivityData::WIFI;
      break;
    } else {
      type = proto::ConnectivityData::MOBILE;
    }
  }

  close(sockfd);
  return type;
}

} // namespace profiler
