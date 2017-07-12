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
#ifndef UTILS_CONFIG_H_
#define UTILS_CONFIG_H_

#include "proto/agent_service.grpc.pb.h"

namespace profiler {

// This is a Unix abstract socket name that is passed to bind() with the
// '@' replaced by '\0'. It designates an abstract socket of name
// "AndroidStudioProfilerAgent" (removing the "@" prefix).
const char* const kAgentSocketName = "@AndroidStudioProfilerAgent";

// Command line argument to be used when looking for the config file path.
const char* const kConfigFileArg = "-config_file";

// Default config file path if none are found on the command line. The path
// points to a profiler::proto::AgentConfig file.
const char* const kConfigFileDefaultPath = "/data/local/tmp/perfd/agent.config";

// The command line argument indicating that perfd is establishing communication
// channel with the agent through Unix abstract socket.
const char* const kConnectCmdLineArg = "-connect";

// Control messages that are sent by Perfd to Perfa via unix socket.
// Also see profiler::ConnectAndSendDataToPerfa for more details on how each
// message is used.
const char* const kHeartBeatRequest = "H";
const char* const kPerfdConnectRequest = "C";

// Default timeout used for grpc calls in which the the grpc target can change.
// In those cases, instead of having the grpc requests block and retry aimlessly
// at a stale target, the requests abort and let users handle any errors.
const int32_t kGrpcTimeoutSec = 1;

class Config {
 public:
  Config(const proto::AgentConfig& agent_config);
  // File path is a string that points to a file that can be parsed by
  // profiler::proto::AgentConfig. The config will be loaded in the
  // constructor.
  explicit Config(const std::string& file_path);

  const proto::AgentConfig& GetAgentConfig() const { return agent_config_; }

  const std::string& GetConfigFilePath() const { return config_file_path_; }

  // A helper method to set timeout relative to system_clock::now() on |context|
  static void SetClientContextTimeout(grpc::ClientContext* context,
                                      int32_t to_sec = 0, int32_t to_msec = 0);

 private:
  proto::AgentConfig agent_config_;
  std::string config_file_path_;
};

}  // namespace profiler

#endif  // UTILS_CONFIG_H_
