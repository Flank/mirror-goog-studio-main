/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "nonblocking_command_runner.h"

#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sstream>

#include "utils/log.h"
#include "utils/thread_name.h"

using profiler::Log;
using std::string;

namespace profiler {

// Const for index in pipe to read from
// Note: Child processes read from to stdin_pipe[kPipeRead] and parent processes
// write to stdin_pip[kPipeWrite]
const uint kPipeRead = 0;
// Const for index in pipe to write to
// Note: Child processes write to stdout_pipe[kPipeWrite] and parent processes
// read from stdout_pipe[kPipeRead]
const uint kPipeWrite = 1;

bool NonBlockingCommandRunner::Run(const char* const arguments[],
                                   const string& input,
                                   StdoutCallback* callback,
                                   const char* const env_args[]) {
  // stdin_pipe referrs to the stdin of the child.
  int stdin_pipe[2];
  // stdout_pipe referrs to the stdout of the child.
  int stdout_pipe[2];

  if (log_command_) {
    Log::D("Forking Command: %s", executable_path_.c_str());
  }

  if (pipe(stdin_pipe) == -1) {
    Log::E("Failed to open stdin pipe: %s.", strerror(errno));
    return false;
  }
  if (pipe(stdout_pipe) == -1) {
    Log::E("Failed to open stdout pipe: %s.", strerror(errno));
    return false;
  }
  child_process_id_ = fork();
  if (child_process_id_ == 0) {
    // child continues here
    // close the write portion of the stdin_pipe since pipes are one direction
    // only, and we will read from this pipe.
    close(stdin_pipe[kPipeWrite]);
    // close the read portion of the stdout_pipe since we will write to this
    // pipe only.
    close(stdout_pipe[kPipeRead]);
    // redirect stdin
    if (stdin_pipe[kPipeRead] != STDIN_FILENO) {
      dup2(stdin_pipe[kPipeRead], STDIN_FILENO);
      close(stdin_pipe[kPipeRead]);
    }
    // redirect stdout
    if (stdout_pipe[kPipeWrite] != STDOUT_FILENO) {
      dup2(stdout_pipe[kPipeWrite], STDOUT_FILENO);
      close(stdout_pipe[kPipeWrite]);
    }

    // run child process image
    execve(executable_path_.c_str(), (char* const*)arguments,
           (char* const*)env_args);
    // if we get here at all, an error occurred, but we are in the child
    // process, so just exit
    Log::W("Child process exited with code %d", errno);
    _exit(EXIT_FAILURE);
  } else if (child_process_id_ > 0) {
    // parent continues here
    // close unused file descriptors, these are for child only
    close(stdin_pipe[kPipeRead]);
    close(stdout_pipe[kPipeWrite]);
    if (!input.empty()) {
      // open a handle to pipe input to.
      FILE* handle = fdopen(stdin_pipe[kPipeWrite], "wb");
      fwrite(input.c_str(), sizeof(char), input.size(), handle);
      // closing the handle interrupts the input stream (required to end the
      // input).
      fclose(handle);
    }
    if (callback != nullptr) {
      read_data_thread_ = std::thread([callback, stdout_pipe]() -> void {
        SetThreadName("Studio::CommandRunner");
        (*callback)(stdout_pipe[kPipeRead]);
        close(stdout_pipe[kPipeRead]);
      });
    } else {
      close(stdout_pipe[kPipeRead]);
    }
  } else {
    // failed to create child
    close(stdin_pipe[kPipeRead]);
    close(stdin_pipe[kPipeWrite]);
    close(stdout_pipe[kPipeRead]);
    close(stdout_pipe[kPipeWrite]);
    return false;
  }
  return true;
}

void NonBlockingCommandRunner::Kill() {
  if (IsRunning()) {
    int status;
    kill(child_process_id_, SIGINT);
    waitpid(child_process_id_, &status, 0);
    child_process_id_ = 0;
    if (read_data_thread_.joinable()) {
      read_data_thread_.join();
    }
  }
}

}  // namespace profiler
