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

#include "tools/base/deploy/installer/live_literal_update.h"

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
#include "tools/base/deploy/sites/sites.h"

namespace {
// TODO: From BaseSwapCommand

// These values are based on FIRST_APPLICATION_UID and LAST_APPLICATION_UID in
// android.os.Process, which we assume are stable since they haven't been
// changed since 2012.
const int kFirstAppUid = 10000;
const int kLastAppUid = 19999;

}  // namespace

namespace deploy {

void LiveLiteralUpdateCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_live_literal_request()) {
    return;
  }

  request_ = request.live_literal_request();

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  SetUpdateParameters(request_.package_name(), pids, request_.extra_agents());
  ready_to_run_ = true;
}

// TODO: Refactor this which is mostly identical to BaseSwapCommand::Run()
void LiveLiteralUpdateCommand::Run(proto::InstallerResponse* response) {
  proto::LiveLiteralUpdateResponse* update_response =
      response->mutable_live_literal_response();

  if (!ExtractBinaries(workspace_.GetTmpFolder(),
                       {kAgent, kAgentAlt, kInstallServer})) {
    update_response->set_status(proto::LiveLiteralUpdateResponse::SETUP_FAILED);
    ErrEvent("Extracting binaries failed");
    return;
  }

  client_ = AppServers::Get(request_.package_name(), workspace_.GetTmpFolder(),
                            workspace_.GetVersion());

  PrepareAndBuildRequest(update_response);
  Update(request_, update_response);
  ProcessResponse(update_response);
}

bool LiveLiteralUpdateCommand::CheckFilesExist(
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

// TODO: Refactor. Taken partly from OverlaySwapCOmmand::PrepareAndBuildRequest.
// TODO: This function should return a bool and its return value should be
//       checked.
void LiveLiteralUpdateCommand::PrepareAndBuildRequest(
    proto::LiveLiteralUpdateResponse* response) {
  std::string version = workspace_.GetVersion() + "-";

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent =
      request_.arch() == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  std::string agent = kAgent;
#endif

  std::string startup_path = Sites::AppStartupAgent(package_name_);
  std::string studio_path = Sites::AppStudio(package_name_);
  std::string agent_path = startup_path + version + agent;

  std::unordered_set<std::string> missing_files;
  if (!CheckFilesExist({startup_path, studio_path, agent_path},
                       &missing_files)) {
    ErrEvent("LiveLiteral: CheckFilesExist failed");
    return;
  }

  RunasExecutor run_as(package_name_);
  std::string error;

  if (missing_files.find(startup_path) != missing_files.end() &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    response->set_status(proto::LiveLiteralUpdateResponse::SETUP_FAILED);
    ErrEvent("Could not create startup agent directory: " + error);
  }

  if (missing_files.find(studio_path) != missing_files.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    response->set_status(proto::LiveLiteralUpdateResponse::SETUP_FAILED);
    ErrEvent("Could not create .studio directory: " + error);
  }

  if (missing_files.find(agent_path) != missing_files.end() &&
      !run_as.Run("cp", {"-F", workspace_.GetTmpFolder() + agent, agent_path},
                  nullptr, &error)) {
    response->set_status(proto::LiveLiteralUpdateResponse::SETUP_FAILED);
    ErrEvent("Could not copy binaries: " + error);
  }

  agent_path_ = agent_path;
}

// TODO: Refactor this which is mostly identical to
// OverlaySwapCOmmand::GetAgentLogs()
void LiveLiteralUpdateCommand::GetAgentLogs(
    proto::LiveLiteralUpdateResponse* response) {
  Phase p("GetAgentLogs");
  proto::GetAgentExceptionLogRequest req;
  req.set_package_name(request_.package_name());

  // If this fails, we don't really care - it's a best-effort situation; don't
  // break the deployment because of it. Just log and move on.
  auto resp = client_->GetAgentExceptionLog(req);
  if (!resp) {
    Log::W("Could not write to server to retrieve agent logs.");
    return;
  }

  for (const auto& log : resp->logs()) {
    auto added = response->add_agent_logs();
    *added = log;
  }
}

// TODO: Refactor this which is mostly identical to
// OverlaySwapCommand::ProcessResponse()
void LiveLiteralUpdateCommand::ProcessResponse(
    proto::LiveLiteralUpdateResponse* response) {
  Phase p("Live LiveLiteralUpdate");

  // Do this even if the deployment failed; it's retrieving data unrelated to
  // the current deployment. We might want to find a better time to do this.
  GetAgentLogs(response);
}

// TODO: Refactor this which is mostly identical to
// BaseSwapCommand::FilterProcessIds()
void LiveLiteralUpdateCommand::FilterProcessIds(std::vector<int>* process_ids) {
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

// TODO: Refactor this which is mostly identical to
// BaseSwapCommand::ListenForAgents()
proto::LiveLiteralUpdateResponse::Status
LiveLiteralUpdateCommand::ListenForAgents() {
  Phase("ListenForAgents");
  proto::OpenAgentSocketRequest req;
  req.set_socket_name(GetSocketName());

  auto resp = client_->OpenAgentSocket(req);
  if (!resp) {
    return proto::LiveLiteralUpdateResponse::INSTALL_SERVER_COM_ERR;
  }

  if (resp->status() != proto::OpenAgentSocketResponse::OK) {
    return proto::LiveLiteralUpdateResponse::READY_FOR_AGENTS_NOT_RECEIVED;
  }

  return proto::LiveLiteralUpdateResponse::OK;
}

// TODO: Refactor this which is mostly identical to BaseSwapCommand::Swap()
void LiveLiteralUpdateCommand::Update(
    const proto::LiveLiteralUpdateRequest& request,
    proto::LiveLiteralUpdateResponse* response) {
  Phase p("LiveLiteralUpdate");
  if (response->status() != proto::LiveLiteralUpdateResponse::UNKNOWN) {
    return;
  }

  // Remove process ids that we do not need to swap.
  FilterProcessIds(&process_ids_);

  // Don't bother with the server if we have no work to do.
  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be update Live Literal");
    response->set_status(proto::LiveLiteralUpdateResponse::OK);
    return;
  }

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached (below).
  proto::LiveLiteralUpdateResponse::Status status = ListenForAgents();
  if (status != proto::LiveLiteralUpdateResponse::OK) {
    response->set_status(status);
    return;
  }

  if (!Attach(process_ids_, agent_path_)) {
    response->set_status(proto::LiveLiteralUpdateResponse::AGENT_ATTACH_FAILED);
    return;
  }

  // Request for the install-server to accept a connection for each agent
  // attached. The install-server will forward the specified swap request to
  // every agent, then return an aggregate list of each agent's response.

  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size() + extra_agents_count_);
  *req.mutable_agent_request()->mutable_live_literal_request() = request;

  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    response->set_status(
        proto::LiveLiteralUpdateResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (resp->status() == proto::SendAgentMessageResponse::OK) {
    if (response->failed_agents_size() == 0) {
      response->set_status(proto::LiveLiteralUpdateResponse::OK);
    } else {
      response->set_status(proto::LiveLiteralUpdateResponse::AGENT_ERROR);
    }
    return;
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        response->set_status(
            proto::LiveLiteralUpdateResponse::PROCESS_CRASHING);
        response->set_extra(record.process_name);
        return;
      }

      if (record.not_responding) {
        response->set_status(
            proto::LiveLiteralUpdateResponse::PROCESS_NOT_RESPONDING);
        response->set_extra(record.process_name);
        return;
      }
    }
  }

  for (int pid : request.process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      response->set_status(
          proto::LiveLiteralUpdateResponse::PROCESS_TERMINATED);
      response->set_extra(pid_string);
      return;
    }
  }

  response->set_status(
      proto::LiveLiteralUpdateResponse::MISSING_AGENT_RESPONSES);
}

}  // namespace deploy
