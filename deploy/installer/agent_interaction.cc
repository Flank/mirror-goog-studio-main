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

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/server/app_servers.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

namespace {
uint64_t socket_counter = 0;
std::string kAgent = "agent.so";
std::string kAgentAlt = "agent-alt.so";
}

bool AgentInteractionCommand::PrepareInteraction(proto::Arch arch) {
  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  agent_filename_ = arch == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  agent_filename_ = kAgent;
#endif

  if (package_name_.empty()) {
    ErrEvent("Unable to Prepare interaction without a package name");
    return false;
  }

  // Extract binaries
  std::vector<std::string> to_extract = {agent_filename_, kInstallServer};
  if (!ExtractBinaries(workspace_.GetTmpFolder(), to_extract)) {
    ErrEvent("Extracting binaries failed");
    return false;
  }

  client_ = AppServers::Get(package_name_, workspace_.GetTmpFolder(),
                            workspace_.GetVersion());

  // Before attaching, make sure the agent is where it is expected
  if (!CopyAgent(agent_filename_)) {
    ErrEvent("Unable to Copy() agent");
    return false;
  }

  interaction_prepared_ = true;
  return true;
}

bool AgentInteractionCommand::Attach(const std::vector<int>& pids) {
  Phase p("AttachAgents");

  if (!interaction_prepared_) {
    ErrEvent("Attempted to Attach() without Prepare()");
    return false;
  }

  CmdCommand cmd(workspace_);
  for (int pid : pids) {
    std::string output;
    std::string agent_path = AppAgentAbsPath(agent_filename_);
    LogEvent("Attaching agent: '"_s + agent_path + "'");
    if (!cmd.AttachAgent(pid, agent_path, {GetSocketName()}, &output)) {
      ErrEvent("Could not attach agent to process: "_s + output);
      return false;
    }
  }
  return true;
}

bool AgentInteractionCommand::Attach(
    const google::protobuf::RepeatedField<int>& ppids) {
  std::vector<int> pids;
  for (int pid : ppids) {
    pids.emplace_back(pid);
  }
  return Attach(pids);
}

std::string AgentInteractionCommand::GetSocketName() {
  if (socket_name_.empty()) {
    socket_name_ = Socket::kDefaultAddressPrefix + to_string(socket_counter++);
  }
  return socket_name_;
}

std::string AgentInteractionCommand::AppAgentAbsPath(
    const std::string& agent_filename) {
  return AppAgentAbsDir() + workspace_.GetVersion() + "-" + agent_filename;
}

std::string AgentInteractionCommand::AppAgentAbsDir() {
  return Sites::AppStartupAgent(package_name_);
}

std::unique_ptr<proto::OpenAgentSocketResponse>
AgentInteractionCommand::ListenForAgents() {
  Phase p("ListenForAgents");
  proto::OpenAgentSocketRequest socket_request;
  socket_request.set_socket_name(GetSocketName());
  return client_->OpenAgentSocket(socket_request);
}

bool AgentInteractionCommand::CheckExist(
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

bool AgentInteractionCommand::CopyAgent(const std::string& agent_filename) {
  Phase p("CopyAgent()");
  std::string version = workspace_.GetVersion() + "-";

  std::string startup_path = AppAgentAbsDir();
  std::string studio_path = Sites::AppStudio(package_name_);
  std::string agent_path = AppAgentAbsPath(agent_filename);

  std::unordered_set<std::string> missing;
  if (!CheckExist({startup_path, studio_path, agent_path}, &missing)) {
    ErrEvent("AgentInteractionCommand: CheckExist failed");
    return false;
  }

  RunasExecutor run_as(package_name_);
  std::string error;

  bool missing_startup = missing.find(startup_path) != missing.end();
  bool missing_agent = missing.find(agent_path) != missing.end();

  // Clean up other agents from the startup_agent directory. Because agents are
  // versioned (agent-<version#>) we cannot simply copy our agent on top of the
  // previous file. If the startup_agent directory exists but our agent cannot
  // be found in it, we assume another agent is present and delete it.
  if (!missing_startup && missing_agent) {
    if (!run_as.Run("rm", {"-f", "-r", startup_path}, nullptr, &error)) {
      ErrEvent("Could not remove old agents: " + error);
      return false;
    }
    missing_startup = true;
  }

  if (missing_startup &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    ErrEvent("Could not create startup agent directory: " + error);
    return false;
  }

  if (missing.find(studio_path) != missing.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    ErrEvent("Could not create .studio directory: " + error);
    return false;
  }

  if (missing_agent &&
      !run_as.Run(
          "cp", {"-F", workspace_.GetTmpFolder() + agent_filename, agent_path},
          nullptr, &error)) {
    ErrEvent("Could not copy binaries: " + error);
    return false;
  }

  return true;
}

void AgentInteractionCommand::FilterProcessIds(std::vector<int>* process_ids) {
  Phase p("FilterProcessIds");

  // These values are based on FIRST_APPLICATION_UID and LAST_APPLICATION_UID in
  // android.os.Process, which we assume are stable since they haven't been
  // changed since 2012.
  const int kFirstAppUid = 10000;
  const int kLastAppUid = 19999;

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
std::unique_ptr<proto::GetAgentExceptionLogResponse>
AgentInteractionCommand::GetAgentLogs() {
  Phase p("GetAgentLogs");
  proto::GetAgentExceptionLogRequest req;
  req.set_package_name(package_name_);
  return client_->GetAgentExceptionLog(req);
}

}  // namespace deploy
