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

#include "tools/base/deploy/installer/executor.h"

#include <iostream>
#include <numeric>

#include <fcntl.h>
#include <poll.h>
#include <stdlib.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"

using std::string;
namespace deploy {

const string kRunAsExecutable = "/system/bin/run-as";

const size_t kStdinFileBufferSize = 64 * 1024;
const size_t kReadBufferSize = 64 * 1024;

// Pump stdin_source > child_stdin
//      child_stdout > output
//      child_strerr > error
static void Pump(int stdin_source, int child_stdin, int child_stdout,
                 std::string* output, int child_stderr, std::string* error) {
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

  char* stdin_buffer = (char*)malloc(kStdinFileBufferSize);
  size_t buffer_offset = 0;
  size_t buffer_size = 0;
  buffer_size = read(stdin_source, stdin_buffer, kStdinFileBufferSize);

  char* buffer = (char*)malloc(kReadBufferSize);
  int hups = 0;
  if (buffer_size > 0) {
    hups = 1;
  }

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
  free(buffer);
  free(stdin_buffer);
}

static bool PrivateRun(const std::string& executable_path,
                       const std::vector<std::string>& args,
                       std::string* output, std::string* error,
                       int input_file_fd) {
  int child_stdout, child_stdin, child_stderr, child_pid, status;
  bool ok = Executor::ForkAndExec(executable_path, args, &child_stdin,
                                  &child_stdout, &child_stderr, &child_pid);
  if (!ok) {
    *error = "Unable to ForkAndExec";
    return false;
  }

  Pump(input_file_fd, child_stdin, child_stdout, output, child_stderr, error);

  close(child_stdin);
  close(child_stdout);
  close(child_stderr);

  // Retrieve status from child process.
  int pid = waitpid(child_pid, &status, 0);
  bool result = (pid == child_pid);
  if (!result) {
    ErrEvent("waitpid returned " + to_string(pid) +
             " but expected:" + to_string(child_pid));
    return false;
  }
  return WIFEXITED(status) && (WEXITSTATUS(status) == 0);
}

bool Executor::RunWithInput(const std::string& executable_path,
                            const std::vector<std::string>& args,
                            std::string* output, std::string* error,
                            const std::string& input_file) noexcept {
  int stdin_source = open(input_file.c_str(), O_RDONLY, 0);
  bool result = PrivateRun(executable_path, args, output, error, stdin_source);
  close(stdin_source);
  return result;
}

bool Executor::Run(const std::string& executable_path,
                   const std::vector<std::string>& args, std::string* output,
                   std::string* error) noexcept {
  // Create an empty input fd for the pump
  int p[2];
  int err = pipe(p);
  if (err != 0) {
    *error = "Unable to pipe() while executing " + executable_path;
    return false;
  }
  close(p[1]);
  close(p[0]);
  bool result = PrivateRun(executable_path, args, output, error, p[0]);
  return result;
}

bool Executor::RunAs(const std::string& executable_path,
                     const std::string& package_name,
                     const std::vector<std::string>& parameters,
                     std::string* output, std::string* error) noexcept {
  std::vector<std::string> args;
  args.push_back(package_name);
  args.push_back(executable_path),
      args.insert(args.end(), parameters.begin(), parameters.end());
  return Run(kRunAsExecutable, args, output, error);
}

bool Executor::ForkAndExecAs(const std::string& executable_path,
                             const std::string& package_name,
                             const std::vector<std::string>& parameters,
                             int* child_stdin_fd, int* child_stdout_fd,
                             int* child_stderr_fd, int* fork_pid) noexcept {
  std::vector<std::string> args;
  args.push_back(package_name);
  args.push_back(executable_path);
  args.insert(args.end(), parameters.begin(), parameters.end());
  return ForkAndExec(kRunAsExecutable, args, child_stdin_fd, child_stdout_fd,
                     child_stderr_fd, fork_pid);
}

bool Executor::ForkAndExec(const std::string& executable_path,
                           const std::vector<std::string>& args,
                           int* child_stdin_fd, int* child_stdout_fd,
                           int* child_stderr_fd, int* fork_pid) noexcept {
  //  std::string cmd = executable_path;
  //  for (const std::string& arg : args) {
  //    cmd.append(" ");
  //    cmd.append(arg);
  //  }
  //  LogEvent(cmd);
  int stdin_pipe[2], stdout_pipe[2], stderr_pipe[2];
  if (pipe(stdin_pipe) || pipe(stdout_pipe) || pipe(stderr_pipe)) {
    return false;
  }

  // Make sure our pending stdout/err do not become part of the child process
  std::cout << std::flush;
  std::cerr << std::flush;

  *fork_pid = fork();
  if (*fork_pid == 0) {
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

    const char** argv = new const char*[args.size() + 2];
    argv[0] = executable_path.c_str();
    for (int i = 0; i < args.size(); i++) {
      argv[i + 1] = args[i].c_str();
    }
    argv[args.size() + 1] = nullptr;
    execvp(executable_path.c_str(), (char* const*)argv);
    delete[] argv;

    // We need to kill the child process; otherwise, we have two installers.
    exit(1);
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