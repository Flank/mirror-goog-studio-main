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
#include "utils/socket_utils.h"

#include <netinet/in.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

namespace profiler {

// Field 'sun_path' in struct sockaddr_un is 108-char large.
const int kSunPathLength = 108;

void SetUnixSocketAddr(const char* name, struct sockaddr_un* addr_un,
                       socklen_t* addr_len) {
  memset(addr_un, 0, sizeof(*addr_un));
  addr_un->sun_family = AF_UNIX;
  // Field 'sun_path' in struct sockaddr_un is 108-char large.
  size_t length = strnlen(name, kSunPathLength);
  strncpy(addr_un->sun_path, name, kSunPathLength);
  *addr_len = offsetof(struct sockaddr_un, sun_path) + length;

  if (addr_un->sun_path[0] == '@') {
    addr_un->sun_path[0] = '\0';
  }
}

int CreateUnixSocket(const char* address) {
  int fd = -1;
  struct sockaddr_un addr_un;
  socklen_t addr_len;

  if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
    perror("socket error");
    exit(-1);
  }

  SetUnixSocketAddr(address, &addr_un, &addr_len);
  if (bind(fd, (struct sockaddr*)&addr_un, addr_len) == -1) {
    perror("bind error");
    exit(-1);
  }
  return fd;
}

int ListenToSocket(int fd) {
  if (listen(fd, 5) == -1) {
    perror("listen error");
    exit(-1);
  }
  return fd;
}

}  // namespace profiler
