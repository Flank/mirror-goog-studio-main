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

#include "tools/base/deploy/installer/overlay_swap.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"

namespace deploy {

void OverlaySwapCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;
  if (!wrapper.Read(&data)) {
    return;
  }

  if (!request_.ParseFromString(data)) {
    return;
  }

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  SetSwapParameters(request_.package_name(), pids, request_.extra_agents());
  ready_to_run_ = true;
}

proto::SwapRequest OverlaySwapCommand::PrepareAndBuildRequest(
    proto::SwapResponse* response) {
  Phase p("PreSwap");
  proto::SwapRequest request;

  std::string version = workspace_.GetVersion() + "-";
  std::string code_cache = "/data/data/" + package_name_ + "/code_cache/";

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent =
      request_.arch() == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  std::string agent = kAgent;
#endif

  std::string startup_path = code_cache + "startup_agents/";
  std::string studio_path = code_cache + ".studio/";
  std::string agent_path = startup_path + version + agent;

  // TODO: Remove once agent_server functionality is migrated to install_server
  std::string agent_server_path = studio_path + version + kAgentServer;

  std::unordered_set<std::string> missing_files;
  // TODO: Error checking
  CheckFilesExist({startup_path, studio_path, agent_path, agent_server_path},
                  &missing_files);

  RunasExecutor run_as(package_name_, workspace_.GetExecutor());
  std::string error;

  if (missing_files.find(startup_path) != missing_files.end() &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Could not create startup agent directory: " + error);
    return request;
  }

  if (missing_files.find(studio_path) != missing_files.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Could not create .studio directory: " + error);
    return request;
  }

  std::unordered_map<std::string, std::string> copies;
  copies[workspace_.GetTmpFolder() + agent] = agent_path;
  copies[workspace_.GetTmpFolder() + kAgentServer] = agent_server_path;

  for (const auto& entry : copies) {
    if (missing_files.find(entry.second) != missing_files.end() &&
        !run_as.Run("cp", {"-F", entry.first, entry.second}, nullptr, &error)) {
      response->set_status(proto::SwapResponse::SETUP_FAILED);
      ErrEvent("Could not copy binaries: " + error);
      return request;
    }
  }

  SetAgentPaths(agent_path, agent_server_path);
  PopulateClasses(request_.overlay_update().added_files(), &request);
  PopulateClasses(request_.overlay_update().modified_files(), &request);

  request.set_package_name(package_name_);
  request.set_restart_activity(request_.restart_activity());
  return request;
}

void OverlaySwapCommand::PopulateClasses(
    google::protobuf::RepeatedPtrField<proto::OverlayFile> overlay_files,
    proto::SwapRequest* swap_request) {
  for (auto& file : overlay_files) {
    if (file.type() == proto::OverlayFile::DEX) {
      auto def = swap_request->add_modified_classes();
      def->set_name(file.name());
      def->set_dex(file.content());
    }
  }
}

void OverlaySwapCommand::ProcessResponse(proto::SwapResponse* response) {
  Phase p("PostSwap");
  if (response->status() != proto::SwapResponse::OK) {
    return;
  }

  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  install_request.set_allocated_overlay_request(
      request_.release_overlay_update());
  if (!client_->Write(install_request)) {
    response->set_status(proto::SwapResponse::WRITE_TO_SERVER_FAILED);
    return;
  }

  // Wait for server overlay update response.
  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    response->set_status(proto::SwapResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  if (install_response.overlay_response().status() !=
      proto::OverlayUpdateResponse::OK) {
    response->set_status(proto::SwapResponse::INSTALLATION_FAILED);
  }

  // Wait for server exit message.
  if (!client_->Read(&install_response)) {
    response->set_status(proto::SwapResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  // Convert proto events to events.
  for (int i = 0; i < install_response.events_size(); i++) {
    const proto::Event& event = install_response.events(i);
    AddRawEvent(ConvertProtoEventToEvent(event));
  }
}

}  // namespace deploy