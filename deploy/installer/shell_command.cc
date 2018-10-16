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

#include "tools/base/deploy/installer/shell_command.h"

#include <iostream>

#include <fcntl.h>
#include <poll.h>
#include <stdlib.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/trace.h"

using std::string;
namespace deploy {

const string kRunAsExecutable = "/system/bin/run-as";

const size_t kStdinFileBufferSize = 64 * 1024;
const size_t kReadBufferSize = 64 * 1024;

ShellCommandRunner::ShellCommandRunner(const string& executable_path)
    : executable_path_(executable_path) {}

bool ShellCommandRunner::Run(const string& parameters, string* output) const {
  string cmd;
  cmd.append(executable_path_);
  if (!parameters.empty()) {
    cmd.append(" ");
    cmd.append(parameters);
  }
  return RunAndReadOutput(cmd, output);
}

bool ShellCommandRunner::RunAs(const string& package_name,
                               const string& parameters, string* output) const {
  // This assumes "run as" was installed correctly and to the specified
  // location. We should validate this.
  string cmd = kRunAsExecutable;
  cmd.append(" ");
  cmd.append(package_name);
  cmd.append(" ");
  cmd.append(executable_path_);
  cmd.append(" ");
  cmd.append(parameters);

  return RunAndReadOutput(cmd, output);
}

bool ShellCommandRunner::RunAndReadOutput(const string& cmd,
                                          string* output) const {
  Trace trace(cmd);
  char buffer[1024];
  string redirected_cmd = cmd + " 2>&1";
  FILE* pipe = popen(redirected_cmd.c_str(), "r");
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
  if (WEXITSTATUS(ret) != 0 && output != nullptr) {
    *output = std::string() + "Command: '" + cmd + "' failed with output: '" +
              *output + "'\n";
  }
  return WEXITSTATUS(ret) == 0;
}

bool ShellCommandRunner::Run(const std::vector<std::string>& parameters,
                             const std::string& input_file, std::string* output,
                             std::string* error) const {
  int child_stdout, child_stdin, child_stderr;
  std::vector<std::string> args;
  args.push_back(executable_path_);
  args.insert(args.end(), parameters.begin(), parameters.end());
  if (!ForkAndExec(executable_path_, args, &child_stdin, &child_stdout,
                   &child_stderr)) {
    return false;
  }

  pollfd fds[3];
  fds[0].fd = child_stdin;
  fds[0].events = POLLOUT;
  fds[1].fd = child_stdout;
  fds[1].events = POLLIN;
  fds[2].fd = child_stderr;
  fds[2].events = POLLIN;

  std::string* strings[3];
  strings[0] = nullptr;
  strings[1] = output;
  strings[2] = error;

  fcntl(child_stdin, F_SETFL, O_NONBLOCK);
  fcntl(child_stdout, F_SETFL, O_NONBLOCK);
  fcntl(child_stderr, F_SETFL, O_NONBLOCK);
  int stdin_source = open(input_file.c_str(), O_RDONLY, 0);
  char* stdin_buffer = (char*)malloc(kStdinFileBufferSize);
  size_t buffer_offset = 0;
  size_t buffer_size = read(stdin_source, stdin_buffer, kStdinFileBufferSize);

  char* buffer = (char*)malloc(kReadBufferSize);
  int hups = 0;

  while (hups < 3 && poll(fds, 3, -1) > 0) {
    if (fds[0].revents & POLLOUT) {
      size_t wr = write(child_stdin, stdin_buffer + buffer_offset, buffer_size);
      if (wr > 0) {
        buffer_size -= wr;
        buffer_offset += wr;
        if (!buffer_size) {
          // Reload the buffer from the input file file
          buffer_offset = 0;
          buffer_size = read(stdin_source, stdin_buffer, kStdinFileBufferSize);
          if (!buffer_size) {
            hups++;
            fds[0].fd = -1;
          }
        }
      }
    }

    for (int i = 0; i < 3; i++) {
      if (fds[i].fd >= 0) {
        if (fds[i].revents & POLLIN) {
          size_t bytes = read(fds[i].fd, buffer, kReadBufferSize);
          if (strings[i]) {
            strings[i]->append(buffer, bytes);
          }
        }

        if (fds[i].revents & POLLHUP) {
          hups++;
          fds[i].fd = -1;
        }
      }
    }
  }

  close(child_stdin);
  close(child_stdout);
  close(child_stderr);
  free(buffer);
  free(stdin_buffer);
  return true;
}

bool ShellCommandRunner::RunAs(const std::string& package_name,
                               const std::vector<std::string>& parameters,
                               int* child_stdin_fd, int* child_stdout_fd,
                               int* child_stderr_fd) const noexcept {
  std::vector<std::string> args;
  args.push_back(kRunAsExecutable);
  args.push_back(package_name);
  args.push_back(executable_path_);
  args.insert(args.end(), parameters.begin(), parameters.end());
  return ForkAndExec(kRunAsExecutable, args, child_stdin_fd, child_stdout_fd,
                     child_stderr_fd);
}

// Forks and execs |executable| with the given args and returns the file
// descriptors open for stdin stdout and stderr of the child process.
bool ShellCommandRunner::ForkAndExec(const std::string& executable,
                                     const std::vector<std::string> args,
                                     int* child_stdin_fd, int* child_stdout_fd,
                                     int* child_stderr_fd) const {
  int stdin_pipe[2], stdout_pipe[2], stderr_pipe[2];
  if (pipe(stdin_pipe) || pipe(stdout_pipe) || pipe(stderr_pipe)) {
    return false;
  }

  // Make sure our pending stdout/err do not become part of the child process
  std::cout << std::flush;
  std::cerr << std::flush;

  int fork_pid = fork();
  if (fork_pid == 0) {
    // Child
    close(stdin_pipe[1]);
    close(stdout_pipe[0]);
    close(stderr_pipe[0]);

    // Map the output of the parent-write pipe to stdin and the input of the
    // parent-read pipe to stdout. This lets us communicate between the
    // swap_server and the installer.
    dup2(stdin_pipe[0], STDIN_FILENO);
    dup2(stdout_pipe[1], STDOUT_FILENO);
    dup2(stderr_pipe[1], STDERR_FILENO);

    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    close(stderr_pipe[1]);

    char** argv = new char*[args.size() + 1];
    for (int i = 0; i < args.size(); i++) {
      argv[i] = const_cast<char*>(args[i].c_str());
    }
    argv[args.size()] = nullptr;
    execvp(executable.c_str(), argv);
    delete[] argv;
    return false;
  }
  // Parent
  close(stdin_pipe[0]);
  close(stdout_pipe[1]);
  close(stderr_pipe[1]);

  *child_stdin_fd = stdin_pipe[1];
  *child_stdout_fd = stdout_pipe[0];
  *child_stderr_fd = stderr_pipe[0];
  return true;
}
}  // namespace deploy