/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef UTILS_DAEMON_CONFIG_H_
#define UTILS_DAEMON_CONFIG_H_

#include "proto/transport.grpc.pb.h"

namespace profiler {

class DaemonConfig {
 public:
  DaemonConfig(const proto::DaemonConfig& daemon_config);
  // File path is a string that points to a file that can be parsed by
  // profiler::proto::DaemonConfig. The config will be loaded in the
  // constructor.
  explicit DaemonConfig(const std::string& file_path);

  const proto::DaemonConfig& GetConfig() const { return daemon_config_; }

 private:
  proto::DaemonConfig daemon_config_;
};

}  // namespace profiler

#endif  // UTILS_DAEMON_CONFIG_H_
