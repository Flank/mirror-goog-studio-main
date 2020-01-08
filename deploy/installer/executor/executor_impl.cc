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

#include "tools/base/deploy/installer/executor/executor_impl.h"

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

const size_t kReadBufferSize = 64 * 1024;

enum PipeEnd { READ = 0, WRITE = 1 };

// Pump child_stdout > output
//      child_strerr > error
void ExecutorImpl::Pump(int child_stdout, std::string* output, int child_stderr,
                        std::string* error) const {
  pollfd fds[2];
  fds[0].fd = child_stdout;
  fds[0].events = POLLIN;
  fds[1].fd = child_stderr;
  fds[1].events = POLLIN;

  std::string* strings[2];
  strings[0] = output;
  strings[1] = error;

  fcntl(child_stdout, F_SETFL, O_NONBLOCK);
  fcntl(child_stderr, F_SETFL, O_NONBLOCK);

  char* buffer = (char*)malloc(kReadBufferSize);
  int hups = 0;

  while (hups < 2 && poll(fds, 2, -1) > 0) {
    for (int i = 0; i < 2; i++) {
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
}

bool ExecutorImpl::Run(const std::string& executable_path,
                       const std::vector<std::string>& args,
                       std::string* output, std::string* error) const {
  int child_stdout, child_stdin, child_stderr, child_pid, status;
  bool ok = ForkAndExec(executable_path, args, &child_stdin, &child_stdout,
                        &child_stderr, &child_pid);
  if (!ok) {
    *error = "Unable to ForkAndExec";
    return false;
  }

  Pump(child_stdout, output, child_stderr, error);

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

bool ExecutorImpl::ForkAndExec(const std::string& executable_path,
                               const std::vector<std::string>& args,
                               int* child_stdin_fd, int* child_stdout_fd,
                               int* child_stderr_fd, int* fork_pid) const {
  int stdin_pipe[2];
  if (pipe(stdin_pipe)) {
    return false;
  }

  // Ensure the child process automatically closes the write end of the pipe.
  fcntl(stdin_pipe[WRITE], F_SETFD, FD_CLOEXEC);

  // Return the write end of the pipe for the parent process.
  *child_stdin_fd = stdin_pipe[WRITE];

  return ForkAndExecWithStdinFd(executable_path, args, stdin_pipe[READ],
                                child_stdout_fd, child_stderr_fd, fork_pid);
}

bool ExecutorImpl::ForkAndExecWithStdinFd(const std::string& executable_path,
                                          const std::vector<std::string>& args,
                                          int stdin_fd, int* child_stdout_fd,
                                          int* child_stderr_fd,
                                          int* fork_pid) const {
  int stdout_pipe[2], stderr_pipe[2];
  if (pipe(stdout_pipe) || pipe(stderr_pipe)) {
    return false;
  }

  // Make sure our pending stdout/err do not become part of the child process
  std::cout << std::flush;
  std::cerr << std::flush;

  *fork_pid = fork();
  if (*fork_pid == 0) {
    // Child
    close(stdout_pipe[READ]);
    close(stderr_pipe[READ]);

    // Map the output of the parent-write pipe to stdin and the input of the
    // parent-read pipe to stdout. This lets us communicate between the
    // swap_server and the installer.

    dup2(stdin_fd, STDIN_FILENO);
    if (child_stdout_fd == nullptr) {
      close(STDOUT_FILENO);
      open("/dev/null", O_WRONLY);
    } else {
      dup2(stdout_pipe[WRITE], STDOUT_FILENO);
    }

    if (child_stderr_fd == nullptr) {
      close(STDERR_FILENO);
      open("/dev/null", O_WRONLY);
    } else {
      dup2(stderr_pipe[WRITE], STDERR_FILENO);
    }

    close(stdin_fd);
    close(stdout_pipe[WRITE]);
    close(stderr_pipe[WRITE]);

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
  close(stdin_fd);
  close(stdout_pipe[WRITE]);
  close(stderr_pipe[WRITE]);

  if (child_stdout_fd != nullptr) {
    *child_stdout_fd = stdout_pipe[READ];
  }
  if (child_stderr_fd != nullptr) {
    *child_stderr_fd = stderr_pipe[READ];
  }

  return true;
}
}  // namespace deploy