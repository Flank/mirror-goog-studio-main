/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "tools/base/deploy/installer/network_test.h"

#include "tools/base/deploy/common/utils.h"

namespace deploy {
void NetworkTestCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_network_test_request()) {
    return;
  }
  request_received_time_ns_ = deploy::GetTime();
  const auto& test_request = request.network_test_request();
  data_size_bytes_ = test_request.response_data_size();
  ready_to_run_ = true;
}

void NetworkTestCommand::Run(proto::InstallerResponse* response) {
  Phase p("Command Network Test");
  auto test_response = response->mutable_network_test_response();
  if (data_size_bytes_ > 0) {
    std::string* payload = new std::string();
    payload->resize(data_size_bytes_);
    int fd = open("/dev/random", O_RDONLY);
    read(fd, payload->data(), payload->size());
    close(fd);
    test_response->set_allocated_data(payload);
  }
  test_response->set_current_time_ns(request_received_time_ns_);
  test_response->set_processing_duration_ns(deploy::GetTime() -
                                            request_received_time_ns_);
}
}  // namespace deploy
