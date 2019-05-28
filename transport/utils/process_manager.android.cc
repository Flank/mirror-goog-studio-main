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

// Command to attach an jvmti agent. It should be followed with two parameters:
// 1. the app/package name, 2. the location of the agent so.
const char* const kAttachAgentCmd = "cmd activity attach-agent";

namespace profiler {
int ProcessManager::GetPidForBinary(const std::string& binary_name) const {
  // Retrieve the list of all processes on the system.
  vector<Process> processes = this->GetAllProcesses();

  // Search process
  for (Process process : processes) {
    if (process.binary_name == binary_name) {
      return process.pid;
    }
  }
  return -1;
}

vector<Process> ProcessManager::GetAllProcesses() const {
  Trace trace("ProcesManager::GetAllProcesses");
  vector<Process> processes;

  // List /proc/ and retrieve:
  // - /proc/pid for process id.
  // - /proc/pid/cmd for command-line.
  // Only if both values were retrieved the process is added to the return
  // vector.
  DiskFileSystem fs;

  fs.WalkDir("/proc",
             [&](const PathStat& path_stat) {
               if (path_stat.type() != PathStat::Type::DIR) return;

               int pid = atoi(path_stat.rel_path().c_str());
               if (pid == 0) return;

               std::stringstream cmd_path;
               cmd_path << "/proc/" << pid << "/cmdline";

               shared_ptr<File> cmdline_file = fs.GetFile(cmd_path.str());
               if (!cmdline_file->Exists())
                 return;  // The process already died.

               string cmdline = cmdline_file->Contents();
               // cmdline contains a sequence of null terminated string. We want
               // to keep only the first one to extract the binary name.
               string binary_name = string(cmdline, 0, strlen(cmdline.c_str()));
               processes.push_back(Process(pid, cmdline, binary_name));
             },
             1);
  return processes;
}

bool ProcessManager::IsPidAlive(int pid) const {
  DiskFileSystem fs;
  std::stringstream process_path;
  process_path << "/proc/";
  process_path << pid;
  return fs.GetDir(process_path.str())->Exists();
}

std::string ProcessManager::GetCmdlineForPid(int pid) {
  DiskFileSystem file_system;
  std::ostringstream cmd_line_path;
  cmd_line_path << "/proc/" << pid << "/cmdline";
  std::shared_ptr<File> cmd_line_file =
      file_system.GetFile(cmd_line_path.str());
  if (cmd_line_file == nullptr || !cmd_line_file->Exists()) {
    return "";
  }
  return cmd_line_file->ContentsTrimmed();
}

std::string ProcessManager::GetAttachAgentCommand() { return kAttachAgentCmd; }

std::string ProcessManager::GetAttachAgentParams(
    const std::string& app_name, const std::string& data_path,
    const std::string& config_path, const std::string& lib_file_name) {
  std::ostringstream attach_params;
  attach_params << app_name << " " << data_path << "/code_cache/"
                << lib_file_name << "=" << config_path;
  return attach_params.str();
}

std::string ProcessManager::GetPackageNameFromAppName(
    const std::string& app_name) {
  return app_name.substr(0, app_name.find(":"));
}

Process::Process(pid_t pid, const string& cmdline,
                 const std::string& binary_name)
    : pid(pid), cmdline(cmdline), binary_name(binary_name) {}

}  // namespace profiler
