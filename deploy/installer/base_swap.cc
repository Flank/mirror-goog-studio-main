/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "tools/base/deploy/installer/base_swap.h"

#include <fcntl.h>
#include <sys/wait.h>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/install_server.h"

const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =
    S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;

namespace deploy {

void BaseSwapCommand::Run(proto::InstallerResponse* response) {
  proto::SwapResponse* swap_response = response->mutable_swap_response();

  if (!ExtractBinaries(workspace_.GetTmpFolder(),
                       {kAgent, kAgentAlt, kAgentServer, kInstallServer})) {
    swap_response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Extracting binaries failed");
    return;
  }

  client_ = StartInstallServer(
      workspace_.GetExecutor(), workspace_.GetTmpFolder() + kInstallServer,
      package_name_, kInstallServer + "-" + workspace_.GetVersion());

  if (!client_) {
    swap_response->set_status(proto::SwapResponse::START_SERVER_FAILED);
    swap_response->set_extra(kInstallServer);
    return;
  }

  proto::SwapRequest request = PrepareAndBuildRequest(swap_response);
  Swap(request, swap_response);
  ProcessResponse(swap_response);
}

void BaseSwapCommand::Swap(const proto::SwapRequest& request,
                           proto::SwapResponse* response) {
  Phase p("Swap");
  if (response->status() != proto::SwapResponse::UNKNOWN) {
    return;
  }

  // Don't bother with the server if we have no work to do.
  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be swapped");
    response->set_status(proto::SwapResponse::OK);
    return;
  }

  // Start the server and wait for it to begin listening for connections.
  int read_fd, write_fd, agent_server_pid, status;
  if (!StartAgentServer(process_ids_.size() + extra_agents_count_,
                        &agent_server_pid, &read_fd, &write_fd)) {
    response->set_status(proto::SwapResponse::START_SERVER_FAILED);
    EndPhase();
    return;
  }

  ProtoPipe server_input(write_fd);
  ProtoPipe server_output(read_fd);

  if (!AttachAgents()) {
    waitpid(agent_server_pid, &status, 0);
    response->set_status(proto::SwapResponse::AGENT_ATTACH_FAILED);
    return;
  }

  if (!server_input.Write(request)) {
    waitpid(agent_server_pid, &status, 0);
    response->set_status(proto::SwapResponse::WRITE_TO_SERVER_FAILED);
    return;
  }

  std::unordered_map<int, proto::AgentSwapResponse> agent_responses;
  proto::AgentSwapResponse agent_response;
  while (server_output.Read(-1, &agent_response)) {
    agent_responses[agent_response.pid()] = agent_response;

    // Convert proto events to events.
    for (int i = 0; i < agent_response.events_size(); i++) {
      const proto::Event& event = agent_response.events(i);
      AddRawEvent(ConvertProtoEventToEvent(event));
    }

    if (agent_response.status() != proto::AgentSwapResponse::OK) {
      auto failed_agent = response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  // Cleanup zombie agent-server status from the kernel.
  waitpid(agent_server_pid, &status, 0);

  int expected = process_ids_.size() + extra_agents_count_;
  if (agent_responses.size() == expected) {
    if (response->failed_agents_size() == 0) {
      response->set_status(proto::SwapResponse::OK);
    } else {
      response->set_status(proto::SwapResponse::AGENT_ERROR);
    }
    return;
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        response->set_status(proto::SwapResponse::PROCESS_CRASHING);
        response->set_extra(record.process_name);
        return;
      }

      if (record.not_responding) {
        response->set_status(proto::SwapResponse::PROCESS_NOT_RESPONDING);
        response->set_extra(record.process_name);
        return;
      }
    }
  }

  for (int pid : request.process_ids()) {
    const std::string pid_string = to_string(pid);
    if (access(("/proc/" + pid_string).c_str(), F_OK) != 0) {
      response->set_status(proto::SwapResponse::PROCESS_TERMINATED);
      response->set_extra(pid_string);
      return;
    }
  }

  response->set_status(proto::SwapResponse::MISSING_AGENT_RESPONSES);
}

bool BaseSwapCommand::StartAgentServer(int agent_count, int* server_pid,
                                       int* read_fd, int* write_fd) const {
  Phase("StartAgentServer");
  int sync_pipe[2];
  if (pipe(sync_pipe) != 0) {
    ErrEvent("Could not create sync pipe");
    return false;
  }

  const int sync_read_fd = sync_pipe[0];
  const int sync_write_fd = sync_pipe[1];

  // The server doesn't know about the read end of the pipe, so set it to
  // close-on-exec. Not a big deal if this fails, but we should still log.
  if (fcntl(sync_read_fd, F_SETFD, FD_CLOEXEC) == -1) {
    LogEvent("Could not set sync pipe read end to close-on-exec");
  }

  std::vector<std::string> parameters;
  parameters.push_back(to_string(agent_count));
  parameters.push_back(Socket::kDefaultAddress);

  // Pass the write end of the sync pipe to the server. The server will close
  // the pipe to indicate that it is ready to receive connections.
  parameters.push_back(to_string(sync_write_fd));

  int err_fd = -1;
  RunasExecutor run_as(package_name_, workspace_.GetExecutor());
  bool success = run_as.ForkAndExec(agent_server_path_, parameters, write_fd,
                                    read_fd, &err_fd, server_pid);
  close(sync_write_fd);
  close(err_fd);

  if (success) {
    // This branch is only possible in the parent process; we only reach this
    // conditional in the child if success is false (the server failed to run).
    char unused;
    if (read(sync_read_fd, &unused, 1) != 0) {
      ErrEvent("Unexpected response received on sync pipe");
    }
  }

  close(sync_read_fd);
  return success;
}

bool BaseSwapCommand::AttachAgents() const {
  Phase p("AttachAgents");
  CmdCommand cmd(workspace_);
  for (int pid : process_ids_) {
    std::string output;
    LogEvent("Attaching agent: '"_s + agent_path_ + "'");
    output = "";
    if (!cmd.AttachAgent(pid, agent_path_, {Socket::kDefaultAddress},
                         &output)) {
      ErrEvent("Could not attach agent to process: "_s + output);
      return false;
    }
  }
  return true;
}

bool BaseSwapCommand::CheckFilesExist(
    const std::vector<std::string>& files,
    std::unordered_set<std::string>* missing_files) {
  proto::InstallServerRequest request;
  request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  for (const std::string& file : files) {
    request.mutable_check_request()->add_files(file);
  }
  proto::InstallServerResponse response;
  if (!client_->Write(request) || !client_->Read(&response)) {
    return false;
  }

  missing_files->insert(response.check_response().missing_files().begin(),
                        response.check_response().missing_files().end());
  return true;
}

bool BaseSwapCommand::ExtractBinaries(
    const std::string& target_dir,
    const std::vector<std::string>& files_to_extract) const {
  Phase p("ExtractBinaries");

  std::vector<std::unique_ptr<matryoshka::Doll>> dolls;
  for (const std::string& file : files_to_extract) {
    const std::string tmp_path = target_dir + file;

    // If we've already extracted the file, we don't need to re-extract.
    if (access(tmp_path.c_str(), F_OK) == 0) {
      continue;
    }

    // Open the matryoshka if we haven't already done so.
    if (dolls.empty() && !matryoshka::Open(dolls)) {
      ErrEvent("Installer binary does not contain any other binaries.");
      return false;
    }

    // Find the binary that corresponds to this file and write it to disk.
    matryoshka::Doll* doll = matryoshka::FindByName(dolls, file);
    if (!doll) {
      continue;
    }

    if (!WriteArrayToDisk(doll->content, doll->content_len,
                          target_dir + file)) {
      ErrEvent("Failed writing to disk");
      return false;
    }
  }

  return true;
}

bool BaseSwapCommand::WriteArrayToDisk(const unsigned char* array,
                                       uint64_t array_len,
                                       const std::string& dst_path) const
    noexcept {
  Phase p("WriteArrayToDisk");
  std::string real_path = workspace_.GetRoot() + dst_path;
  int fd = open(real_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    ErrEvent("WriteArrayToDisk, open: "_s + strerror(errno));
    return false;
  }
  int written = write(fd, array, array_len);
  if (written == -1) {
    ErrEvent("WriteArrayToDisk, write: "_s + strerror(errno));
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    ErrEvent("WriteArrayToDisk, close: "_s + strerror(errno));
    return false;
  }

  chmod(real_path.c_str(), kRxFileMode);
  return true;
}

}  // namespace deploy