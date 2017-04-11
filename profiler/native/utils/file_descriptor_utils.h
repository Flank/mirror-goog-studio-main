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
#ifndef UTILS_FILE_DESCRIPTOR_UTILS_H_
#define UTILS_FILE_DESCRIPTOR_UTILS_H_

namespace profiler {

// Send a file descriptor, |send_fd|, through a socket designated by
// |through_fd|.
void SendFdThroughFd(int send_fd, int through_fd);

// Receive a file descriptor through a socket designated by
// |through_fd|. Returns the received fd.
int ReceiveFdThroughFd(int through_fd);

}  // namespace profiler

#endif  // UTILS_FILE_DESCRIPTOR_UTILS_H_
