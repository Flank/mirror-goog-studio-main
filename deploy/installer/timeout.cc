/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "tools/base/deploy/installer/timeout.h"

#include "tools/base/deploy/common/utils.h"

namespace deploy {
void TimeoutCommand::ParseParameters(const proto::InstallerRequest& request) {
  if (!request.has_timeout_request()) {
    return;
  }

  const auto& timeout_request = request.timeout_request();
  timeout_ms_ = timeout_request.timeout_ms();

  // As a safety measure, only accept values up to 10s.
  if (timeout_ms_ > max_timeout_ms_) {
    ErrEvent("Requested timeout value is too high (max=" +
             to_string(max_timeout_ms_) + ")");
    return;
  }
  ready_to_run_ = true;
}

void TimeoutCommand::Run(proto::InstallerResponse* response) {
  Phase p("Command Timeout");
  usleep(timeout_ms_ * 1000);
  auto timeout_response = response->mutable_timeout_response();
  timeout_response->set_status(proto::TimeoutResponse_Status_OK);
}
}  // namespace deploy