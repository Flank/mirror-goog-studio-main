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
#include "perfd/profiler_service.h"

#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#include <sstream>
#include <string>
#include "perfd/connector.h"
#include "perfd/generic_component.h"
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

using grpc::ServerContext;
using grpc::ServerWriter;
using grpc::Status;
using profiler::proto::AgentStatusResponse;
using std::string;

namespace profiler {

namespace {

// Connector is a program that inherits (since it is invoked by execl()) a
// client socket already connected to the daemon and passes the socket to the
// agent. This is technically an implementation detail of perfd due to Android's
// security restriction. Therefore, we frame the functionality into "perfd
// -connect". However, in the point of view of process relationship, we use
// connector as the process name (not binary name) for the ease of description.
const char* const kConnectorFileName = "perfd";
// On-device path of the connector program relative to an app's data folder.
const char* const kConnectorRelativePath = "./perfd";
// Name of the jvmti agent library.
const char* const kAgentLibFileName = "libperfa.so";
// Name of the jar file containining the java classes (dex'd) which our
// instrumentation code needs to reference. This jar file will be added to the
// app via the jvmti agent.
const char* const kAgentJarFileName = "perfa.jar";
// Command to attach an jvmti agent. It should be followed with two parameters:
// 1. the app/package name, 2. the location of the agent so.
const char* const kAttachAgentCmd = "cmd activity attach-agent";

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
  connect_arg << kConnectCmdLineArg << "=" << app_pid;

  // Pass the fd as command line argument to connector.
  std::ostringstream fd_arg;
  fd_arg << kPerfdConnectRequest << "=" << fd;
  int return_value =
      execl(kRunAsExecutable, kRunAsExecutable, package_name.c_str(),
            kConnectorRelativePath, connect_arg.str().c_str(),
            fd_arg.str().c_str(), (char*)nullptr);
  if (return_value == -1) {
    perror("execl");
    exit(-1);
  }
}

// Copy over the agent so and jar to the package's directory as specified by
// |package_name| and invoke attach-agent on the app as specified by |app_name|
bool RunAgent(const string& app_name, const string& package_name, const std::string& config_path) {
  CopyFileToPackageFolder(package_name, kAgentJarFileName);
  CopyFileToPackageFolder(package_name, kAgentLibFileName);
  string data_path;
  string error;
  PackageManager package_manager;
  bool success =
      package_manager.GetAppDataPath(package_name, &data_path, &error);
  if (success) {
    std::ostringstream attach_params;
    attach_params << app_name << " " << data_path << "/" << kAgentLibFileName
                  << "=" << config_path;
    BashCommandRunner attach(kAttachAgentCmd);
    success |= attach.Run(attach_params.str(), &error);
  }

  return success;
}

}  // namespace

Status ProfilerServiceImpl::GetCurrentTime(
    ServerContext* context, const profiler::proto::TimeRequest* request,
    profiler::proto::TimeResponse* response) {
  Trace trace("PRO:GetTimes");

  response->set_timestamp_ns(clock_.GetCurrentTime());
  // TODO: Move this to utils.
  timeval time;
  gettimeofday(&time, nullptr);
  // Not specifying LL may cause overflow depending on the underlying type of
  // time.tv_sec.
  int64_t t = time.tv_sec * 1000000LL + time.tv_usec;
  response->set_epoch_timestamp_us(t);
  return Status::OK;
}

Status ProfilerServiceImpl::GetVersion(
    ServerContext* context, const profiler::proto::VersionRequest* request,
    profiler::proto::VersionResponse* response) {
  response->set_version(profiler::kAndroidStudioVersion);
  return Status::OK;
}

Status ProfilerServiceImpl::GetBytes(
    ServerContext* context, const profiler::proto::BytesRequest* request,
    profiler::proto::BytesResponse* response) {
  response->set_contents(file_cache_.GetFile(request->id())->Contents());
  return Status::OK;
}

Status ProfilerServiceImpl::GetAgentStatus(
    ServerContext* context, const profiler::proto::AgentStatusRequest* request,
    profiler::proto::AgentStatusResponse* response) {
  auto got = heartbeat_timestamp_map_.find(request->process_id());
  if (got != heartbeat_timestamp_map_.end()) {
    int64_t current_time = clock_.GetCurrentTime();
    if (GenericComponent::kHeartbeatThresholdNs >
        (current_time - got->second)) {
      response->set_status(AgentStatusResponse::ATTACHED);
    } else {
      response->set_status(AgentStatusResponse::DETACHED);
    }
    response->set_last_timestamp(got->second);
  } else {
    response->set_status(AgentStatusResponse::DETACHED);
    response->set_last_timestamp(INT64_MIN);
  }

  return Status::OK;
}

Status ProfilerServiceImpl::GetDevices(
    ServerContext* context, const profiler::proto::GetDevicesRequest* request,
    profiler::proto::GetDevicesResponse* response) {
  Trace trace("PRO:GetDevices");
  profiler::proto::Device* device = response->add_device();
  string device_id;
  FileReader::Read("/proc/sys/kernel/random/boot_id", &device_id);
  device->set_boot_id(device_id);
  return Status::OK;
}

Status ProfilerServiceImpl::AttachAgent(
    grpc::ServerContext* context,
    const profiler::proto::AgentAttachRequest* request,
    profiler::proto::AgentAttachResponse* response) {
  if (profiler::DeviceInfo::feature_level() < 26) {
    return ::grpc::Status(
        ::grpc::StatusCode::UNIMPLEMENTED,
        "Attaching agent not implemented on Nougat or older devices");
  } else {
    string app_name = ProcessManager::GetCmdlineForPid(request->process_id());
    if (app_name.empty()) {
      Log::V("Process (PID %d) isn't running. Cannot attach agent.",
             request->process_id());
      response->set_status(
          profiler::proto::AgentAttachResponse::FAILURE_UNKNOWN);
      return Status::OK;
    }

    // Copies the connector over to the package's data folder so we can run it
    // to send messages to perfa's Unix socket server.
    string package_name = ProcessManager::GetPackageNameFromAppName(app_name);
    CopyFileToPackageFolder(package_name, kConnectorFileName);
    if (!IsAppAgentAlive(request->process_id(), app_name.c_str())) {
      // Only attach agent if one is not detected.
      if (RunAgent(app_name, package_name, config_.GetConfigFilePath())) {
        response->set_status(profiler::proto::AgentAttachResponse::SUCCESS);
      } else {
        response->set_status(
            profiler::proto::AgentAttachResponse::FAILURE_UNKNOWN);
      }
    }

    int fork_pid = fork();
    if (fork_pid == -1) {
      perror("fork connector");
      return ::grpc::Status(::grpc::StatusCode::RESOURCE_EXHAUSTED,
                            "Cannot fork a process to run connector");
    } else if (fork_pid == 0) {
      // child process
      RunConnector(request->process_id(), package_name, kDaemonSocketName);
      // RunConnector calls execl() at the end. It returns only if an error
      // has occured.
      exit(EXIT_FAILURE);
    }

    return Status::OK;
  }
}

// Runs the connector as the application user and tries to send a message
// (e.g. |kHeartBeatRequest|) to the agent via unix socket. If the agent's
// unix socket server is up, the send operation should be sucessful, in which
// case this will return true, false otherwise.
bool ProfilerServiceImpl::IsAppAgentAlive(int app_pid, const char* app_name) {
  std::ostringstream args;
  args << kConnectCmdLineArg << "=" << app_pid << " " << kHeartBeatRequest;
  BashCommandRunner ping(kConnectorRelativePath);
  return ping.RunAs(args.str(), app_name, nullptr);
}

}  // namespace profiler
