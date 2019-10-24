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
#include <sys/wait.h>
#include <unistd.h>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>

#include "commands/attach_agent.h"
#include "connector.h"
#include "utils/android_studio_version.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/package_manager.h"
#include "utils/process_manager.h"
#include "utils/socket_utils.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

using grpc::Service;
using grpc::Status;
using grpc::StatusCode;
using profiler::proto::AgentData;
using profiler::proto::Event;
using std::string;

namespace profiler {
namespace {

// Connector is a program that inherits (since it is invoked by execl()) a
// client socket already connected to the daemon and passes the socket to the
// agent. This is technically an implementation detail of daemon due to
// Android's security restriction. Therefore, we frame the functionality into
// "daemon -connect". However, in the point of view of process relationship, we
// use connector as the process name (not binary name) for the ease of
// description.
const char* const kConnectorFileName = "transport";
// The subdirectory in app's data folder to contain files that are code.
const char* const kCodeCacheRelativeDir = "./code_cache/";
// On-device path of the connector program relative to an app's data folder.
const char* const kConnectorRelativePath = "./code_cache/transport";
// Name of the jar file containining the java classes (dex'd) which our
// instrumentation code needs to reference. This jar file will be added to the
// app via the jvmti agent.
const char* const kAgentJarFileName = "perfa.jar";

// Delete file executable from package's data folder.
void DeleteFileFromPackageFolder(const string& package_name,
                                 const string& file_name) {
  BashCommandRunner rm{"rm"};
  std::ostringstream args;
  args << "-f " << kCodeCacheRelativeDir << file_name;
  if (!rm.RunAs(args.str(), package_name, nullptr)) {
    perror("rm");
  }
}

// Copy file executable from this process's folder (daemon's folder) to
// package's data folder.
void CopyFileToPackageFolder(const string& package_name,
                             const string& file_name) {
  // Remove old agent first to avoid attaching mismatched version of agent.
  // If old agent exists and it fails to copy the new one, the app would attach
  // the old one and some weird bugs may occur.
  // After removing old agent, the app would fail to attach the agent with a
  // 'file not found' error.
  DeleteFileFromPackageFolder(package_name, file_name);

  BashCommandRunner cp{"cp"};
  std::ostringstream args;
  args << CurrentProcess::dir() << file_name << " " << kCodeCacheRelativeDir;
  if (!cp.RunAs(args.str(), package_name, nullptr)) {
    perror("cp");
  }
}

// Use execl() and run-as to run connector which will establish the
// communication between daemon and the agent.
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
  connect_arg << ":" << kDaemonConnectRequest << ":" << fd;

  int return_value = -1;
  if (DeviceInfo::is_user_build()) {
    return_value = execl(kRunAsExecutable, kRunAsExecutable,
                         package_name.c_str(), kConnectorRelativePath,
                         connect_arg.str().c_str(), (char*)nullptr);
  } else {
    std::ostringstream connector_absolute_path;
    connector_absolute_path << "/data/data/" << package_name << "/"
                            << kConnectorRelativePath;
    return_value = execl(kSuExecutable, kSuExecutable, "root",
                         connector_absolute_path.str().c_str(),
                         connect_arg.str().c_str(), (char*)nullptr);
  }
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
    BashCommandRunner attach(ProcessManager::GetAttachAgentCommand(), true);
    success |= attach.Run(attach_params, &error);
  }

  return success;
}

}  // namespace

Daemon::Daemon(Clock* clock, DaemonConfig* config, FileCache* file_cache,
               EventBuffer* buffer)
    : clock_(clock),
      config_(config),
      file_cache_(file_cache),
      buffer_(buffer),
      transport_component_(new TransportComponent(this)) {}

Daemon::~Daemon() {
  agent_status_is_running_.exchange(false);
  if (agent_status_thread_.joinable()) {
    agent_status_thread_.join();
  }
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
  // Register comman services and command handlers.
  builder_.RegisterService(transport_component_->GetPublicService());
  builder_.RegisterService(transport_component_->GetInternalService());
  RegisterCommandHandler(proto::Command::ATTACH_AGENT, &AttachAgent::Create);

  agent_status_thread_ = std::thread(&Daemon::RunAgentStatusThread, this);

  // According to GRPC documentation, the |port| passed to AddListeningPort()
  // will be "populated with the port number bound to the grpc::Server for the
  // corresponding endpoint after it is successfully bound by BuildAndStart(), 0
  // otherwise. AddListeningPort does not modify this pointer."
  int port = 0;
  builder_.AddListeningPort(server_address, grpc::InsecureServerCredentials(),
                            &port);
  std::unique_ptr<grpc::Server> server(builder_.BuildAndStart());
  if (port == 0) {
    // The port wasn't successfully bound to the server by BuildAndStart().
    const char* error = "Server failed to start. A port number wasn't bound.";
    std::cout << error << std::endl;
    Log::E(error);
    exit(EXIT_FAILURE);
  }
  std::ostringstream oss;
  oss << "Server listening on " << server_address << " port:" << port;
  std::cout << oss.str() << std::endl;
  Log::V(oss.str().c_str());
  server->Wait();  // Block until the server shuts down.
}

bool Daemon::TryAttachAppAgent(int32_t app_pid, const std::string& app_name,
                               const string& agent_lib_file_name,
                               const std::string& agent_config_path) {
  assert(profiler::DeviceInfo::feature_level() >= profiler::DeviceInfo::O);

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
  // exist if we have profiled the same app before, and either Studio/daemon
  // has restarted and has lost any knowledge about such agent.
  if (!IsAppAgentAlive(app_pid, package_name)) {
    RunAgent(app_name, package_name, agent_config_path, agent_lib_file_name);
  }

  // Only reconnect to perfa if an existing connection has not been detected.
  // This can be identified by whether perfa has a valid grpc channel to send
  // this daemon instance the heartbeats.
  if (!CheckAppHeartBeat(app_pid)) {
    int fork_pid = fork();
    if (fork_pid == -1) {
      perror("fork connector");
      return false;
    } else if (fork_pid == 0) {
      // child process
      string socket_name;
      socket_name.append(config_->GetConfig().common().service_socket_name());
      RunConnector(app_pid, package_name, socket_name);
      // RunConnector calls execl() at the end. It returns only if an error
      // has occurred.
      exit(EXIT_FAILURE);
    }
    // Call waitpid() from the parent process, waiting for the child process so
    // it will not be in zombie state after it dies. When a child process is in
    // zombie state, the operating system's Live-LocK Daemon may kill the parent
    // process.
    //
    // Creates a new thread so the waitpid() function will not add the execution
    // time of this function.
    // clang-format off
    std::thread([fork_pid] {
      SetThreadName("Studio:WaitConn");
      int status = 0;
      waitpid(fork_pid, &status, 0);
    }).detach();
    // clang-format on
  }

  return true;
}

grpc::Status Daemon::Execute(const proto::Command& command_data,
                             std::function<void(void)> post) {
  std::lock_guard<std::mutex> lock(mutex_);
  grpc::Status status = grpc::Status::OK;

  // If a handler for the command is registered in the daemon, handle it here.
  auto search = commands_.find(command_data.type());
  if (search != commands_.end()) {
    std::unique_ptr<Command> command((search->second)(command_data));
    status = command->ExecuteOn(this);
  }
  post();

  // Forward every command to agent. It's up to the agent to decide whether to
  // handle it or not.
  transport_component_->ForwardCommandToAgent(command_data);

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
  if (CheckAppHeartBeat(pid)) {
    return AgentData::ATTACHED;
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
  if (profiler::DeviceInfo::feature_level() < profiler::DeviceInfo::O) {
    // pre-O, since the agent is deployed with the app, we should receive a
    // heartbeat right away. We can simply use that as a signal to determine
    // whether an agent can be attached.
    // Note: This will only be called if we have not had a heartbeat yet, so
    // we will return unspecified by default.
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
  std::lock_guard<std::mutex> lock(heartbeat_mutex_);
  return heartbeat_timestamp_map_.find(app_pid) !=
         heartbeat_timestamp_map_.end();
}

void Daemon::SetHeartBeatTimestamp(int32_t app_pid, int64_t timestamp) {
  std::lock_guard<std::mutex> lock(heartbeat_mutex_);
  if (heartbeat_timestamp_map_.find(app_pid) ==
      heartbeat_timestamp_map_.end()) {
    // Call the callback if it is the first time we see the process.
    for (auto callback : agent_status_changed_callbacks_) {
      callback(app_pid);
    }

    // Generate and send an Event for the new data pipeline
    Event event;
    event.set_pid(app_pid);
    event.set_kind(Event::AGENT);
    auto status = event.mutable_agent_data();
    status->set_status(AgentData::ATTACHED);
    buffer()->Add(event);
  }
  heartbeat_timestamp_map_[app_pid] = timestamp;
}

void Daemon::RunAgentStatusThread() {
  SetThreadName("Studio::AgentStatus");
  while (agent_status_is_running_.load()) {
    {
      std::lock_guard<std::mutex> lock(heartbeat_mutex_);
      int64_t current_time = clock_->GetCurrentTime();
      for (auto map : heartbeat_timestamp_map_) {
        // If we have a heartbeat then we attached the agent once as such we
        // update the status.
        // Call the callback if our heartbeat timeouts.
        if (kHeartbeatThresholdNs > (current_time - map.second)) {
          for (auto callback : agent_status_changed_callbacks_) {
            callback(map.first);
          }
        }
      }
    }
    usleep(Clock::ns_to_us(kHeartbeatThresholdNs));
  }
}

}  // namespace profiler
