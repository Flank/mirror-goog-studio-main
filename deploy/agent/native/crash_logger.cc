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

#include "tools/base/deploy/agent/native/crash_logger.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <sstream>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

CrashLogger CrashLogger::instance_ = CrashLogger();

void CrashLogger::Initialize(const std::string& package_name,
                             proto::AgentExceptionLog::AgentPurpose purpose) {
  instance_.log_init_ns_ = GetTime();
  instance_.log_dir_ = Sites::AppLog(package_name);
  instance_.agent_attach_count_++;
  instance_.agent_purpose_ = purpose;
}

void CrashLogger::LogUnhandledException() const { WriteLog(MakeLog()); }

void CrashLogger::LogInstrumentationFailures(
    const std::vector<std::string>& class_names) const {
  // We don't need to log instrumentation failures for regular swaps; that data
  // is already reported via deployment metrics.
  if (agent_purpose_ != proto::AgentExceptionLog::STARTUP_AGENT) {
    return;
  }
  proto::AgentExceptionLog log = MakeLog();
  for (auto& class_name : class_names) {
    log.add_failed_classes(class_name);
  }
  WriteLog(log);
}

proto::AgentExceptionLog CrashLogger::MakeLog() const {
  proto::AgentExceptionLog log;
  log.set_agent_attach_time_ns(log_init_ns_);
  log.set_agent_attach_count(agent_attach_count_);
  log.set_event_time_ns(deploy::GetTime());
  log.set_agent_purpose(agent_purpose_);
  return log;
}

void CrashLogger::WriteLog(const proto::AgentExceptionLog& log) const {
  // Don't write anything if the log has not been initialized.
  if (!agent_attach_count_) {
    return;
  }

  std::vector<char> bytes(log.ByteSizeLong());
  log.SerializeToArray(bytes.data(), bytes.size());

  IO::mkdir(log_dir_, S_IRWXG | S_IRWXU | S_IRWXO);

  std::ostringstream log_file(log_dir_, std::ostringstream::ate);
  log_file << "agent-" << getpid() << "-" << log.event_time_ns() << ".log";

  // These log files will persist between installations of the app. They are
  // deleted when the install-server recovers them.
  int fd = IO::creat(log_file.str(), S_IRUSR | S_IWUSR);
  if (fd == -1) {
    return;
  }

  write(fd, bytes.data(), bytes.size());
  close(fd);
}

}  // namespace deploy
