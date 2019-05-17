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

#ifndef FORK_COMMAND_H_
#define FORK_COMMAND_H_

#include <functional>
#include <string>
#include <thread>

namespace profiler {

// Runs commands via fork exec.
class NonBlockingCommandRunner {
 public:
  using StdoutCallback = std::function<void(int stdout_fd)>;

  explicit NonBlockingCommandRunner(const std::string& executable_path)
      : NonBlockingCommandRunner(executable_path, false) {}
  explicit NonBlockingCommandRunner(const std::string& executable_path,
                                    bool log_command)
      : executable_path_(executable_path),
        log_command_(log_command),
        child_process_id_(0) {}
  virtual ~NonBlockingCommandRunner() { Kill(); }

  // Fork and execs the executable. The |input| is piped to stdin. The input
  // pipe is closed before returning. If a |callback| is provided a blocking
  // call is made read the stdout from the child process. It is recommended that
  // if you pass a callback either the command runner is run in a new thread, or
  // there is a way to kill the command runner on a new thread.
  bool Run(const char* const arguments[], StdoutCallback* callback);
  bool Run(const char* const arguments[], const std::string& input);
  bool Run(const char* const arguments[], const std::string& input,
           const char* const env_args[]);
  virtual bool Run(const char* const arguments[], const std::string& input,
                   StdoutCallback* callback, const char* const env_args[]);

  // Helper function to read /proc/|child_process_id_|/cmdline and match it against
  // |executable_path_|. The cmdline file is read from multiple times in a retry
  // loop, true is returned if the match is found, otherwise false.
  virtual bool BlockUntilChildprocessExec();

  // Kills the running child process by sending SEGINT then blocks for the
  // process to complete.
  virtual void Kill();
  virtual bool IsRunning() { return child_process_id_ > 0; }

 private:
  const std::string executable_path_;
  // True writes running commands to logs.
  const bool log_command_;
  // Process id of the child process.
  pid_t child_process_id_;
  // Thread to read output on.
  std::thread read_data_thread_;
};

}  // namespace profiler

#endif  // FORK_COMMAND_H_
