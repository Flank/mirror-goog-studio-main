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

namespace {
// When running on host, we are testing. Use a URL as the cmdline of the app.
const char* const kTestAppCmdline = "http://127.0.0.1:";
}  // namespace

namespace profiler {
int ProcessManager::GetPidForBinary(const std::string& binary_name) const {
  // This is the counter part of GetCmdlineForPid(..) where the binary_name is
  // assumed in a well defined format.
  string app_name;
  int32_t pid = -1;
  std::istringstream iss(binary_name);
  iss >> app_name >> pid;
  if (app_name.compare(kTestAppCmdline) == 0 && pid != -1) return pid;
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
  std::ostringstream oss;
  oss << kTestAppCmdline << pid;
  return oss.str();
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
