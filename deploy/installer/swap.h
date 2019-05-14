/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef INSTALLER_SWAP_COMMAND_H_
#define INSTALLER_SWAP_COMMAND_H_

#include <string>
#include <unordered_map>
#include <vector>

#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class SwapCommand : public Command {
 public:
  SwapCommand(Workspace& workspace) : Command(workspace), response_(nullptr) {}
  ~SwapCommand() {}

  void ParseParameters(int argc, char** argv) override;
  void Run() override;

 private:
  std::string request_bytes_;
  proto::SwapRequest request_;
  std::string target_dir_;
  proto::SwapResponse* response_;

  enum class User { SHELL_USER, APP_PACKAGE };

  // Makes sure everything is ready for ART to attach the JVMI agent to the app.
  // - Make sure the agent shared lib is in the app data folder.
  // - Make sure the  configuration file to app data folder.
  bool Setup() noexcept;

  // Performs a swap by starting the server and attaching agents. Returns
  // SwapResponse::Status::OK if the swap succeeds; returns an appropriate error
  // code otherwise.
  proto::SwapResponse::Status Swap() const;

  // Starts the server and waits for it to start listening. The sync is
  // performed by opening a pipe and passing the write end to the server
  // process, then blocking on the read end. The server indicates it is ready to
  // receive connections by closing the write end, which unblocks this method.
  bool WaitForServer(int agent_count, int* server_pid, int* read_fd,
                     int* write_fd) const;

  // Tries to attach an agent to each process in the request; if any agent fails
  // to attach, returns false.
  bool AttachAgents() const;

  // Runs a command with the provided arguments. If run_as_package is true,
  // the command is invoked with 'run-as'. If the command fails, prints the
  // string specified in 'error' to standard error.
  bool RunCmd(const std::string& shell_cmd, User run_as,
              const std::vector<std::string>& args, std::string* output) const;

  // Copies the agent and server binarys from the directory specified by
  // src_path to the destination specified by dst_path.
  bool CopyBinaries(const std::string& src_path,
                    const std::string& dst_path) const noexcept;

  // Given an unsigned character array of length array_len, writes it out as a
  // file to the path specified by dst_path.
  bool WriteArrayToDisk(const unsigned char* array, uint64_t array_len,
                        const std::string& dst_path) const noexcept;
};

}  // namespace deploy

#endif  // INSTALLER_SWAP_COMMAND_H_
