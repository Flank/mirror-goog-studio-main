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
#include "utils/file_descriptor_utils.h"

#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/types.h>

namespace profiler {

namespace {

ssize_t write_fd(int fd, void *ptr, size_t nbytes, int sendfd) {
  struct msghdr msg;
  struct iovec iov[1];

  union {
    struct cmsghdr cm;
    char control[CMSG_SPACE(sizeof(int))];
  } control_un;
  struct cmsghdr *cmptr;

  msg.msg_control = control_un.control;
  msg.msg_controllen = sizeof(control_un.control);

  cmptr = CMSG_FIRSTHDR(&msg);
  cmptr->cmsg_len = CMSG_LEN(sizeof(int));
  cmptr->cmsg_level = SOL_SOCKET;
  cmptr->cmsg_type = SCM_RIGHTS;
  *((int *)CMSG_DATA(cmptr)) = sendfd;

  msg.msg_name = nullptr;
  msg.msg_namelen = 0;

  iov[0].iov_base = ptr;
  iov[0].iov_len = nbytes;
  msg.msg_iov = iov;
  msg.msg_iovlen = 1;

  return (sendmsg(fd, &msg, 0));
}

ssize_t read_fd(int fd, void *ptr, size_t nbytes, int *recvfd) {
  struct msghdr msg;
  struct iovec iov[1];
  ssize_t n;

  union {
    struct cmsghdr cm;
    char control[CMSG_SPACE(sizeof(int))];
  } control_un;
  struct cmsghdr *cmptr;

  msg.msg_control = control_un.control;
  msg.msg_controllen = sizeof(control_un.control);

  msg.msg_name = nullptr;
  msg.msg_namelen = 0;

  iov[0].iov_base = ptr;
  iov[0].iov_len = nbytes;
  msg.msg_iov = iov;
  msg.msg_iovlen = 1;

  if ((n = recvmsg(fd, &msg, 0)) <= 0) return (n);

  if ((cmptr = CMSG_FIRSTHDR(&msg)) != nullptr &&
      cmptr->cmsg_len == CMSG_LEN(sizeof(int))) {
    if (cmptr->cmsg_level != SOL_SOCKET) {
      printf("control level != SOL_SOCKET");
      exit(-1);
    }
    if (cmptr->cmsg_type != SCM_RIGHTS) {
      printf("control type != SCM_RIGHTS");
      exit(-1);
    }
    *recvfd = *((int *)CMSG_DATA(cmptr));
  } else
    *recvfd = -1; /* descriptor was not passed */

  return (n);
}

}  // namespace

void SendFdThroughFd(int send_fd, int through_fd) {
  int buffer;
  int write_return =
      profiler::write_fd(through_fd, &buffer, sizeof(buffer), send_fd);
  if (write_return == -1) {
    perror("sendmsg");
    exit(-1);
  }
}

int ReceiveFdThroughFd(int through_fd) {
  int receive_fd = -1;
  int buffer = -1;
  int read_return =
      profiler::read_fd(through_fd, &buffer, sizeof(buffer), &receive_fd);
  if (read_return == -1) {
    perror("recvmsg");
    exit(-1);
  }
  return receive_fd;
}

}  // namespace profiler
