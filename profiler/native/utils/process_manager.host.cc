#include "process_manager.h"

#include <dirent.h>
#include <stdlib.h>
#include <string.h>
#include <functional>
#include <iostream>
#include <memory>

#include "utils/bash_command.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/trace.h"

using std::shared_ptr;
using std::string;
using std::vector;

namespace profiler {
int ProcessManager::GetPidForBinary(const std::string& binary_name) const {
  // Not required for test.
  // TODO Implement if required.
  return 0;
}

vector<Process> ProcessManager::GetAllProcesses() const {
  Trace trace("ProcesManager::GetAllProcesses");
  vector<Process> processes;
  return processes;
}

bool ProcessManager::IsPidAlive(int pid) const { return true; }

std::string ProcessManager::GetCmdlineForPid(int pid) {
  // To talk to the test framework we issue a curl command to
  // a web server setup by the host. This allows us to communicate
  // in a similar fashion to calling cmd attach-agent on the device.
  std::ostringstream stringStream;
  stringStream << "http://127.0.0.1:" << pid;
  return stringStream.str();
}

std::string ProcessManager::GetPackageNameFromAppName(
    const std::string& app_name) {
  return app_name;
}

std::string ProcessManager::GetAttachAgentCommand() {
  // Use curl to talk to our host client.
  return "curl";
}

std::string ProcessManager::GetAttachAgentParams(
    const std::string& app_name, const std::string& data_path,
    const std::string& config_path, const std::string& lib_file_name) {
  std::ostringstream attach_params;
  attach_params << app_name << "?attach-agent=" << data_path << "/"
                << lib_file_name << "=" << config_path;
  return attach_params.str();
}

Process::Process(pid_t pid, const string& cmdline,
                 const std::string& binary_name)
    : pid(pid), cmdline(cmdline), binary_name(binary_name) {}

}  // namespace profiler
