/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */

#ifndef CRASH_LOGGER_H
#define CRASH_LOGGER_H

#include <string>
#include <vector>

#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

/*
This object logs agent crashes/failures that cannot be caught by the typical
failure reporting mechanisms in the agent.

Unhandled exceptions thrown by the application due to a bad swap or a startup
agent error are likely to occur after the agent server has disconnected, and
cannot be caught normally. They are instead reported by calls to the Log*
methods in this class.

Each call to Log* creates a file in a dedicated folder under the app data
folder: /data/data/<package>/.agent-logs/agent-<pid>-<timestamp>.log

These log files are recovered by the installer via the install-server after a
swap and passed back to the host. At this point, the install-server deletes the
log files. Because of this, "old" log files are only retrieved during subsequent
swaps.
*/
class CrashLogger {
 public:
  static const CrashLogger& Instance() { return instance_; }

  static void Initialize(const std::string& package_name,
                         proto::AgentExceptionLog::AgentPurpose purpose);

  // Writes a log file indicating that an unhandled exception has occurred.
  void LogUnhandledException() const;

  // Writes a log file indicating that a set of classes failed to be
  // instrumented.
  void LogInstrumentationFailures(
      const std::vector<std::string>& class_name) const;

 private:
  static CrashLogger instance_;

  int64_t log_init_ns_;
  std::string log_dir_;
  size_t agent_attach_count_;
  proto::AgentExceptionLog::AgentPurpose agent_purpose_;

  CrashLogger() : agent_attach_count_(0) {}

  proto::AgentExceptionLog MakeLog() const;
  void WriteLog(const proto::AgentExceptionLog& log) const;
};

}  // namespace deploy

#endif
