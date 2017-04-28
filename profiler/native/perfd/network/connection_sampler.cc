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
#include "connection_sampler.h"

#include <inttypes.h>

namespace {

// Returns whether the given address is the ip address "127.0.0.1", coverted to
// ipv4 or ipv6 byte string. In other words, this will match either
// "0100007F" or "0000000000000000FFFF00000100007F".
bool isLoopbackAddress(const char* address) {
  return strcmp("0100007F", address) == 0 ||
         strcmp("0000000000000000FFFF00000100007F", address) == 0;
}

} // namespace

namespace profiler {

using std::string;

void ConnectionSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  int connection_number = 0;
  char buffer[kLineBufferSize];
  for (auto &file_name : files_) {
    connection_number += ReadConnectionNumber(file_name, buffer);
  }
  data->mutable_connection_data()->set_connection_number(connection_number);
}

int ConnectionSampler::ReadConnectionNumber(const string &file, char *buffer) {
  int count = 0;

  FILE *fp = fopen(file.c_str(), "r");
  if (fp == NULL) {
    return count;
  }

  char localAddress[kAddressStringSize];
  char remoteAddress[kAddressStringSize];
  uint32_t idx, uid, number;

  while(fgets(buffer, kLineBufferSize * sizeof(char), fp) != NULL) {
    if (sscanf(buffer,
               " %" SCNu32 ": %32[^:]:%x %32[^:]:%x %x %x:%x %x:%x %x %" SCNu32,
               &idx, localAddress, &number, remoteAddress, &number, &number,
               &number, &number, &number, &number, &number, &uid) == 12 &&
               uid_ == uid && !isLoopbackAddress(localAddress) &&
               !isLoopbackAddress(remoteAddress)) {
      count++;
    }
  }
  fclose(fp);

  return count;
}

}  // namespace profiler
