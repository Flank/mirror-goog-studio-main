#include "bash_command.h"

#include "sys/wait.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/trace.h"

using std::string;

namespace profiler {

bool BashCommandRunner::Run(const string &parameters, string *output) const {
  string cmd;
  cmd.append(executable_path_);
  if (!parameters.empty()) {
    cmd.append(" ");
    cmd.append(parameters);
  }
  return RunAndReadOutput(cmd, output);
}

bool BashCommandRunner::RunAndReadOutput(const string &cmd,
                                         string *output) const {
  Trace trace(executable_path_);
  if (log_command_) Log::D("Running bash command: '%s'", cmd.c_str());
  char buffer[1024];
  FILE *pipe = popen(cmd.c_str(), "r");
  if (pipe == nullptr) {
    return false;
  }

  while (!feof(pipe)) {
    if (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
      if (output != nullptr) {
        output->append(buffer);
      }
    }
  }
  int ret = pclose(pipe);
  return WEXITSTATUS(ret) == 0;
}
}  // namespace profiler
