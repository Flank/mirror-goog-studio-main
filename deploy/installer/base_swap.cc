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
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/app_servers.h"

namespace {
// These values are based on FIRST_APPLICATION_UID and LAST_APPLICATION_UID in
// android.os.Process, which we assume are stable since they haven't been
// changed since 2012.
const int kFirstAppUid = 10000;
const int kLastAppUid = 19999;
}  // namespace

namespace deploy {

void BaseSwapCommand::Run(proto::InstallerResponse* response) {
  proto::SwapResponse* swap_response = response->mutable_swap_response();

  if (!ExtractBinaries(workspace_.GetTmpFolder(),
                       {kAgent, kAgentAlt, kInstallServer})) {
    swap_response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Extracting binaries failed");
    return;
  }

  client_ = AppServers::Get(package_name_, workspace_.GetTmpFolder(),
                            workspace_.GetVersion());

  std::unique_ptr<proto::SwapRequest> request = PrepareAndBuildRequest();
  if (request == nullptr) {
    swap_response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("BaseSwapCommand: Unable to PrepareAndBuildRequest");
    return;
  }

  Swap(std::move(request), swap_response);
  ProcessResponse(swap_response);
}

bool BaseSwapCommand::Swap(
    const std::unique_ptr<proto::SwapRequest> swap_request,
    proto::SwapResponse* swap_response) {
  Phase p("Swap");
  if (swap_response->status() != proto::SwapResponse::UNKNOWN) {
    ErrEvent(
        "BaseSwapCommand: Unable to Swap (swapResponse status is populated)");
    return false;
  }

  // Remove process ids that we do not need to swap.
  FilterProcessIds(&process_ids_);

  // Don't bother with the server if we have no work to do.
  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be swapped");
    swap_response->set_status(proto::SwapResponse::OK);
    return true;
  }

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached (below).
  proto::SwapResponse::Status status = ListenForAgents();
  if (status != proto::SwapResponse::OK) {
    swap_response->set_status(status);
    return false;
  }

  if (!Attach(process_ids_, agent_path_)) {
    swap_response->set_status(proto::SwapResponse::AGENT_ATTACH_FAILED);
    return false;
  }

  // Request for the install-server to accept a connection for each agent
  // attached. The install-server will forward the specified swap request to
  // every agent, then return an aggregate list of each agent's response.
  // TODO: Move this block to its own function.
  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size() + extra_agents_count_);
  *req.mutable_agent_request()->mutable_swap_request() = *swap_request.get();

  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    swap_response->set_status(proto::SwapResponse::INSTALL_SERVER_COM_ERR);
    return false;
  }

  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = swap_response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (resp->status() == proto::SendAgentMessageResponse::OK) {
    if (swap_response->failed_agents_size() == 0) {
      swap_response->set_status(proto::SwapResponse::OK);
      return true;
    } else {
      swap_response->set_status(proto::SwapResponse::AGENT_ERROR);
      return false;
    }
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        swap_response->set_status(proto::SwapResponse::PROCESS_CRASHING);
        swap_response->set_extra(record.process_name);
        return false;
      }

      if (record.not_responding) {
        swap_response->set_status(proto::SwapResponse::PROCESS_NOT_RESPONDING);
        swap_response->set_extra(record.process_name);
        return false;
      }
    }
  }

  for (int pid : swap_request->process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      swap_response->set_status(proto::SwapResponse::PROCESS_TERMINATED);
      swap_response->set_extra(pid_string);
      return false;
    }
  }

  swap_response->set_status(proto::SwapResponse::MISSING_AGENT_RESPONSES);
  return false;
}

void BaseSwapCommand::FilterProcessIds(std::vector<int>* process_ids) {
  Phase p("FilterProcessIds");
  auto it = process_ids->begin();
  while (it != process_ids->end()) {
    const int pid = *it;
    const std::string pid_path = "/proc/" + to_string(pid);
    struct stat proc_dir_stat;
    if (IO::stat(pid_path, &proc_dir_stat) < 0) {
      LogEvent("Ignoring pid '" + to_string(pid) + "'; could not stat().");
      it = process_ids->erase(it);
    } else if (proc_dir_stat.st_uid < kFirstAppUid ||
               proc_dir_stat.st_uid > kLastAppUid) {
      LogEvent("Ignoring pid '" + to_string(pid) +
               "'; uid=" + to_string(proc_dir_stat.st_uid) +
               " is not in the app uid range.");
      it = process_ids->erase(it);
    } else {
      ++it;
    }
  }
}

proto::SwapResponse::Status BaseSwapCommand::ListenForAgents() {
  Phase("ListenForAgents");
  proto::OpenAgentSocketRequest req;
  req.set_socket_name(GetSocketName());

  auto resp = client_->OpenAgentSocket(req);
  if (!resp) {
    return proto::SwapResponse::INSTALL_SERVER_COM_ERR;
  }

  if (resp->status() != proto::OpenAgentSocketResponse::OK) {
    return proto::SwapResponse::READY_FOR_AGENTS_NOT_RECEIVED;
  }

  return proto::SwapResponse::OK;
}

bool BaseSwapCommand::CheckFilesExist(
    const std::vector<std::string>& files,
    std::unordered_set<std::string>* missing_files) {
  proto::CheckSetupRequest req;
  for (const std::string& file : files) {
    req.add_files(file);
  }
  auto resp = client_->CheckSetup(req);
  if (!resp) {
    return false;
  }

  missing_files->insert(resp->missing_files().begin(),
                        resp->missing_files().end());
  return true;
}

}  // namespace deploy
