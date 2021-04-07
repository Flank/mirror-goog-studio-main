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

#include "tools/base/deploy/installer/install_coroutine_agent.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {
void InstallCoroutineAgentCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_install_coroutine_agent_request()) {
    return;
  }

  auto installCoroutineAgentRequest = request.install_coroutine_agent_request();
  request_ = installCoroutineAgentRequest;
  package_name_ = installCoroutineAgentRequest.package_name();

  ready_to_run_ = true;
}

void InstallCoroutineAgentCommand::Run(proto::InstallerResponse* response) {
  Phase p("Install coroutine agent");

  auto install_coroutine_agent_response =
      response->mutable_install_coroutine_agent_response();

  std::string agent_file_name_dst = "coroutine_debugger_agent.so";

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent_file_name_src = request_.arch() == proto::Arch::ARCH_64_BIT
                                        ? "coroutine_debugger_agent64.so"
                                        : "coroutine_debugger_agent.so";
#else
  std::string agent_file_name_src = "coroutine_debugger_agent.so";
#endif

  // extract agent .so from installer
  if (!ExtractBinaries(workspace_.GetTmpFolder(), {agent_file_name_src})) {
    install_coroutine_agent_response->set_status(
        proto::InstallCoroutineAgentResponse::ERROR);
    std::string error_message = "Extracting binaries failed";
    install_coroutine_agent_response->set_error_msg(error_message);
    ErrEvent(error_message);
    return;
  }

  RunasExecutor run_as(package_name_);
  std::string error;
  // copy agent .so into app's code_cache folder
  if (!run_as.Run("cp",
                  {"-F", workspace_.GetTmpFolder() + agent_file_name_src,
                   Sites::AppCodeCache(package_name_) + agent_file_name_dst},
                  nullptr, &error)) {
    install_coroutine_agent_response->set_status(
        proto::InstallCoroutineAgentResponse::ERROR);

    std::string error_message = "Could not copy binaries: " + error;
    install_coroutine_agent_response->set_error_msg(error_message);
    ErrEvent(error_message);
  } else {
    install_coroutine_agent_response->set_status(
        proto::InstallCoroutineAgentResponse::OK);
  }
}
}  // namespace deploy
