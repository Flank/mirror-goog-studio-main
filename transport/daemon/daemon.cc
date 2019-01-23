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
#include "daemon.h"

#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include "connector.h"
#include "utils/android_studio_version.h"
#include "utils/config.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/package_manager.h"
#include "utils/process_manager.h"
#include "utils/socket_utils.h"
#include "utils/trace.h"

using grpc::Service;
using grpc::Status;
using grpc::StatusCode;
using profiler::proto::AgentData;
using std::string;

namespace profiler {
namespace {

// Connector is a program that inherits (since it is invoked by execl()) a
// client socket already connected to the daemon and passes the socket to the
// agent. This is technically an implementation detail of perfd due to Android's
// security restriction. Therefore, we frame the functionality into "perfd
// -connect". However, in the point of view of process relationship, we use
// connector as the process name (not binary name) for the ease of description.
const char* const kConnectorFileName = "transport";
// On-device path of the connector program relative to an app's data folder.
const char* const kConnectorRelativePath = "./transport";
// Name of the jar file containining the java classes (dex'd) which our
// instrumentation code needs to reference. This jar file will be added to the
// app via the jvmti agent.
const char* const kAgentJarFileName = "perfa.jar";

// Delete file executable from package's data folder.
void DeleteFileFromPackageFolder(const string& package_name,
                                 const string& file_name) {
  std::ostringstream os;
  os << kRunAsExecutable << " " << package_name << " rm -f " << file_name;
  if (system(os.str().c_str()) == -1) {
    perror("system");
    exit(-1);
  }
}

// Copy file executable from this process's folder (perfd's folder) to
// package's data folder.
void CopyFileToPackageFolder(const string& package_name,
                             const string& file_name) {
  // Remove old agent first to avoid attaching mismatched version of agent.
  // If old agent exists and it fails to copy the new one, the app would attach
  // the old one and some weird bugs may occur.
  // After removing old agent, the app would fail to attach the agent with a
  // 'file not found' error.
  DeleteFileFromPackageFolder(package_name, file_name);

  std::ostringstream os;
  os << kRunAsExecutable << " " << package_name << " cp "
     << CurrentProcess::dir() << file_name << " .";
  if (system(os.str().c_str()) == -1) {
    perror("system");
    exit(-1);
  }
}

// Use execl() and run-as to run connector which will establish the
// communication between perfd and the agent.
//
// By using execl(), the client-side socket that's connected to daemon can be
// used by connector.
// By using run-as, connector is under the same user as the agent, and thus it
// can talk to the agent who is waiting for the client socket.
void RunConnector(int app_pid, const string& package_name,
                  const string& daemon_address) {
  // Use connect() to create a client socket that can talk to the server.
  int fd;  // The client socket that's connected to daemon.
  if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
    perror("socket error");
    exit(-1);
  }
  struct sockaddr_un addr_un;
  socklen_t addr_len;
  SetUnixSocketAddr(daemon_address.c_str(), &addr_un, &addr_len);
  if (connect(fd, (struct sockaddr*)&addr_un, addr_len) == -1) {
    perror("connect error");
    exit(-1);
  }

  // Pass the app's process id so the connector knows which agent socket to
  // connect to.
  std::ostringstream connect_arg;
  connect_arg << "--" << kConnectCmdLineArg << "=" << app_pid;
  // Pass the fd as command line argument to connector.
  connect_arg << ":" << kPerfdConnectRequest << ":" << fd;

  int return_value =
      execl(kRunAsExecutable, kRunAsExecutable, package_name.c_str(),
            kConnectorRelativePath, connect_arg.str().c_str(), (char*)nullptr);
  if (return_value == -1) {
    perror("execl");
    exit(-1);
  }
}

// Copy over the agent so and jar to the package's directory as specified by
// |package_name| and invoke attach-agent on the app as specified by |app_name|
bool RunAgent(const string& app_name, const string& package_name,
              const std::string& config_path,
              const string& agent_lib_file_name) {
  CopyFileToPackageFolder(package_name, kAgentJarFileName);
  CopyFileToPackageFolder(package_name, agent_lib_file_name);
  string data_path;
  string error;
  PackageManager package_manager;
  bool success =
      package_manager.GetAppDataPath(package_name, &data_path, &error);
  if (success) {
    const std::string& attach_params = ProcessManager::GetAttachAgentParams(
        app_name, data_path, config_path, agent_lib_file_name);
    BashCommandRunner attach(ProcessManager::GetAttachAgentCommand());
    success |= attach.Run(attach_params, &error);
  }

  return success;
}

}  // namespace

Daemon::Daemon(Clock* clock, Config* config, FileCache* file_cache,
               EventBuffer* buffer)
    : clock_(clock),
      config_(config),
      file_cache_(file_cache),
      buffer_(buffer),
      transport_component_(new TransportComponent(this)) {
  builder_.RegisterService(transport_component_->GetPublicService());
  builder_.RegisterService(transport_component_->GetInternalService());
}

void Daemon::RegisterProfilerComponent(
    std::unique_ptr<ServiceComponent> component) {
  if (component == nullptr) return;
  Service* public_service = component->GetPublicService();
  if (public_service != nullptr) {
    builder_.RegisterService(public_service);
  }
  Service* internal_service = component->GetInternalService();
  if (internal_service != nullptr) {
    builder_.RegisterService(internal_service);
  }
  profiler_components_.push_back(std::move(component));
}

void Daemon::RunServer(const string& server_address) {
  int port = 0;
  builder_.AddListeningPort(server_address, grpc::InsecureServerCredentials(),
                            &port);
  std::unique_ptr<grpc::Server> server(builder_.BuildAndStart());
  std::cout << "Server listening on " << server_address << " port:" << port
            << std::endl;
  server->Wait();
}

bool Daemon::TryAttachAppAgent(int32_t app_pid, const std::string& app_name,
                               const string& agent_lib_file_name) {
  assert(profiler::DeviceInfo::feature_level() >= proto::Device::O);

  string package_name = ProcessManager::GetPackageNameFromAppName(app_name);
  PackageManager package_manager;
  string data_path;
  string error;
  if (!package_manager.GetAppDataPath(package_name, &data_path, &error)) {
    // Cannot access the app's data folder.
    return false;
  }

  auto agent_status = GetAgentStatus(app_pid);
  // Only attempt to connect if our status is not unattachable
  if (agent_status == AgentData::UNATTACHABLE) {
    return false;
  }

  // Copies the connector over to the package's data folder so we can run it
  // to send messages to perfa's Unix socket server.
  CopyFileToPackageFolder(package_name, kConnectorFileName);
  // Only attach agent if one is not detected. Note that an agent can already
  // exist if we have profiled the same app before, and either Studio/perfd
  // has restarted and has lost any knowledge about such agent.
  if (!IsAppAgentAlive(app_pid, package_name)) {
    RunAgent(app_name, package_name, config_->GetConfigFilePath(),
             agent_lib_file_name);
  }

  // Only reconnect to perfa if an existing connection has not been detected.
  // This can be identified by whether perfa has a valid grpc channel to send
  // this perfd instance the heartbeats.
  if (!CheckAppHeartBeat(app_pid)) {
    int fork_pid = fork();
    if (fork_pid == -1) {
      perror("fork connector");
      return false;
    } else if (fork_pid == 0) {
      // child process
      string socket_name;
      socket_name.append(config_->GetAgentConfig().service_socket_name());
      RunConnector(app_pid, package_name, socket_name);
      // RunConnector calls execl() at the end. It returns only if an error
      // has occured.
      exit(EXIT_FAILURE);
    }
  }

  return true;
}

grpc::Status Daemon::Execute(const proto::Command& command_data,
                             std::function<void(void)> post) {
  std::lock_guard<std::mutex> lock(mutex_);
  std::unique_ptr<Command> command(
      commands_[command_data.type()](command_data));
  grpc::Status status = command->ExecuteOn(this);
  post();
  return status;
}

grpc::Status Daemon::Execute(const proto::Command& command_data) {
  return Execute(command_data, []() {});
}

std::vector<proto::EventGroup> Daemon::GetEventGroups(
    const proto::GetEventGroupsRequest* request) {
  return buffer_->Get(request->kind(), request->from_timestamp(),
                      request->to_timestamp());
}

// Runs the connector as the application user and tries to send a message
// (e.g. |kHeartBeatRequest|) to the agent via unix socket. If the agent's
// unix socket server is up, the send operation should be sucessful, in which
// case this will return true, false otherwise.
bool Daemon::IsAppAgentAlive(int app_pid, const string& app_name) {
  std::ostringstream args;
  args << "--" << kConnectCmdLineArg << "=" << app_pid << ":"
       << kHeartBeatRequest;
  BashCommandRunner ping(kConnectorRelativePath);
  return ping.RunAs(args.str(), app_name, nullptr);
}

AgentData::Status Daemon::GetAgentStatus(int32_t pid) {
  auto got = agent_status_map_.find(pid);
  if (got != agent_status_map_.end()) {
    return got->second;
  }

  // Only query the app's debuggable state if we haven't already, to
  // avoid calling "run-as" repeatedly.
  auto attachable_itr = agent_attachable_map_.find(pid);
  if (attachable_itr != agent_attachable_map_.end()) {
    return attachable_itr->second ? AgentData::UNSPECIFIED
                                  : AgentData::UNATTACHABLE;
  }
  string app_name = ProcessManager::GetCmdlineForPid(pid);
  if (app_name.empty()) {
    // Process is not available. Do not cache the attachable result here since
    // we couldn't retrieve the process.
    return AgentData::UNATTACHABLE;
  }
  if (profiler::DeviceInfo::feature_level() < proto::Device::O) {
    // pre-O, since the agent is deployed with the app, we should receive a
    // heartbeat right away. We can simply use that as a signal to determine
    // whether an agent can be attached.
    // Note: This will only be called if we have not had a heartbeat yet, so we
    // will return unspecified by default.
    return AgentData::UNSPECIFIED;
  }
  // In O+, we can attach an jvmti agent as long as the app is debuggable
  // and the app's data folder is available to us.
  string package_name = ProcessManager::GetPackageNameFromAppName(app_name);
  PackageManager package_manager;
  string data_path;
  string error;
  bool has_data_path =
      package_manager.GetAppDataPath(package_name, &data_path, &error);
  agent_attachable_map_[pid] = has_data_path;
  return has_data_path ? AgentData::UNSPECIFIED : AgentData::UNATTACHABLE;
}

bool Daemon::CheckAppHeartBeat(int app_pid) {
  auto got = agent_status_map_.find(app_pid);
  if (got != agent_status_map_.end()) {
    return got->second == proto::AgentData::ATTACHED;
  }
  return false;
}

void Daemon::SetHeartBeatTimestamp(int32_t app_pid, int64_t timestamp) {
  heartbeat_timestamp_map_[app_pid] = timestamp;
}

Status Daemon::ConfigureStartupAgent(
    const profiler::proto::ConfigureStartupAgentRequest* request,
    profiler::proto::ConfigureStartupAgentResponse* response) {
  if (profiler::DeviceInfo::feature_level() < proto::Device::O) {
    return Status(StatusCode::UNIMPLEMENTED,
                  "JVMTI agent cannot be attached on Nougat or older devices");
  }
  string package_name = request->app_package_name();
  string agent_lib_file_name = request->agent_lib_file_name();

  CopyFileToPackageFolder(package_name, kAgentJarFileName);
  CopyFileToPackageFolder(package_name, agent_lib_file_name);

  PackageManager package_manager;
  string data_path;
  string error;
  string config_path = config_->GetConfigFilePath();

  string agent_args = "";
  if (package_manager.GetAppDataPath(package_name, &data_path, &error)) {
    agent_args.append(data_path)
        .append("/")
        .append(agent_lib_file_name)
        .append("=")
        .append(config_path);
  }
  response->set_agent_args(agent_args);
  return Status::OK;
}

}  // namespace profiler
