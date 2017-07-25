#include "bash_command.h"

#include "sys/wait.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/trace.h"

using std::string;

namespace profiler {
BashCommandRunner::BashCommandRunner(const string &executable_path)
    : executable_path_(executable_path) {}

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
