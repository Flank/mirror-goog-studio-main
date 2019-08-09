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

#include "tools/base/deploy/installer/command_cmd.h"

#include <iostream>
#include <sstream>

#include <sys/stat.h>
#include <cstdlib>
#include <cstring>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

namespace {
const char* CMD_EXEC = "/system/bin/cmd";

bool IsLeadingSpaceOrTab(const char* buf) {
  return *buf == ' ' || *buf == '\t';
}

const char* Ltrim(const char* buf) {
  while (IsLeadingSpaceOrTab(buf)) {
    buf++;
  }
  return buf;
}

const char* FindEndOfLine(const char* buf) {
  while (*buf != '\n' && *buf != '\0') {
    buf++;
  }
  return buf;
}

const char* FindNextLine(const char* buf) {
  buf = FindEndOfLine(buf);
  if (*buf == '\0') {
    return nullptr;
  }
  return buf + 1;
}
}  // namespace

bool CmdCommand::GetAppApks(const std::string& package_name,
                            std::vector<std::string>* apks,
                            std::string* error_string) const noexcept {
  Trace trace("CmdCommand::GetAppApks");
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("path");
  parameters.emplace_back(package_name);
  std::string out;
  std::string err;
  bool success = workspace_.GetExecutor().Run(CMD_EXEC, parameters, &out, &err);
  if (!success) {
    *error_string = err;
    return false;
  }

  // Parse output
  std::stringstream ss(out);
  std::string line;

  // Return path prefixed with "package:"
  while (std::getline(ss, line, '\n')) {
    if (!strncmp(line.c_str(), "package:", 8)) {
      apks->push_back(std::string(line.c_str() + 8));
    }
  }

  return true;
}

// Use "cmd package dump <pkg_name>" to find the path to the APK.
// Note that this method uses a custom parser to parse through the
// output, since in APIs 24-27 and if the package isn't installed,
// cmd will dump all installed packages.
//
// The custom parser simply looks for the "Dexopt state:" header,
// followed by "[pkg_name]", followed by the "path:" entry.
bool CmdCommand::DumpApks(const std::string& package_name,
                          std::vector<std::string>* apks,
                          std::string* error_string) const noexcept {
  Trace trace("CmdCommand::DumpApks");
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("dump");
  parameters.emplace_back(package_name);
  std::string out;
  std::string err;
  bool success = workspace_.GetExecutor().Run(CMD_EXEC, parameters, &out, &err);
  if (!success) {
    *error_string = err;
    return false;
  }

  bool found_dex_opt_section = false;
  bool found_package = false;
  const char* out_buf = out.c_str();
  while (*out_buf != '\0') {
    const char* line_end = FindEndOfLine(out_buf);
    if (!IsLeadingSpaceOrTab(out_buf)) {
      if (!found_dex_opt_section && !strncmp(out_buf, "Dexopt state:", 13)) {
        found_dex_opt_section = true;
      } else if (found_dex_opt_section) {
        // Perhaps the package wasn't found or it was the last package in the
        // Dexopt list, in which case we should exit the loop now that we found
        // the next section.
        break;
      }
    } else if (found_dex_opt_section) {
      out_buf = Ltrim(out_buf);
      if (found_dex_opt_section && *out_buf == '[') {
        // Look for the closing square bracket from the current position to the
        // end of the line. Note the first character cannot be '['.
        const char* end_tag =
            (const char*)memchr(out_buf, ']', line_end - out_buf);
        // Use the size of "substring" as the limit for strncmp since it is not
        // null-terminated (only package_name is null terminated).
        bool package_match =
            end_tag != nullptr &&
            !strncmp(out_buf + 1, package_name.c_str(), end_tag - out_buf - 1);
        if (package_match) {
          found_package = true;
        } else if (found_package && !package_match) {
          // Exit the loop since we are past the correct package section.
          break;
        }
      } else if (found_package && !strncmp(out_buf, "path:", 5)) {
        out_buf = Ltrim(out_buf + 5);
        std::string path(out_buf, line_end - out_buf);
        apks->push_back(path);
      }
    }
    out_buf = FindNextLine(line_end);
  }
  return true;
}

bool CmdCommand::AttachAgent(int pid, const std::string& agent,
                             const std::string& args,
                             std::string* error_string) const noexcept {
  Trace trace("CmdCommand::AttachAgent");
  std::vector<std::string> parameters;
  parameters.emplace_back("activity");
  parameters.emplace_back("attach-agent");
  parameters.emplace_back(to_string(pid));
  parameters.emplace_back(agent + "=" + args);

  std::string out;
  return workspace_.GetExecutor().Run(CMD_EXEC, parameters, &out, error_string);
}

bool CmdCommand::UpdateAppInfo(const std::string& user_id,
                               const std::string& package_name,
                               std::string* error_string) const noexcept {
  Trace trace("CmdCommand::UpdateAppInfo");
  std::vector<std::string> parameters;
  parameters.emplace_back("activity");
  parameters.emplace_back("update-appinfo");
  parameters.emplace_back(user_id);
  parameters.emplace_back(package_name);

  std::string out;
  return workspace_.GetExecutor().Run(CMD_EXEC, parameters, &out, error_string);
}

int get_file_size(std::string path) {
  struct stat statbuf;
  stat(path.c_str(), &statbuf);
  return statbuf.st_size;
}

bool CmdCommand::CreateInstallSession(
    std::string* output, const std::vector<std::string> options) const
    noexcept {
  Phase p("Create Install Session");
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-create");
  for (const std::string& option : options) {
    parameters.emplace_back(option);
  }
  for (auto& option : options) {
    LogEvent(option);
  }

  std::string err;
  workspace_.GetExecutor().Run(CMD_EXEC, parameters, output, &err);
  std::string match = "Success: created install session [";
  if (output->find(match, 0) != 0) {
    return false;
  }
  *output = output->substr(match.size(), output->size() - match.size() - 2);
  return true;
}

int CmdCommand::PreInstall(const std::vector<std::string>& apks,
                           std::string* output) const noexcept {
  Phase p("Preinstall");
  output->clear();

  std::string session;
  std::vector<std::string> options;
  if (!CreateInstallSession(output, options)) {
    return -1;
  } else {
    session = *output;
  }

  output->clear();
  for (auto& apk : apks) {
    std::string output;
    std::string error;
    std::vector<std::string> parameters;
    parameters.push_back("package");
    parameters.push_back("install-write");
    std::stringstream size;
    size << "-S" << get_file_size(apk);
    parameters.push_back(size.str());
    parameters.push_back(session);
    parameters.push_back(apk.substr(apk.rfind("/") + 1));
    workspace_.GetExecutor().RunWithInput(CMD_EXEC, parameters, &output, &error,
                                          apk);
  }
  return atoi(session.c_str());
}  // namespace deploy

bool CmdCommand::CommitInstall(const std::string& session,
                               std::string* output) const noexcept {
  Phase p("Commit Install");
  output->clear();
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-commit");
  parameters.emplace_back(session);

  for (std::string& parameter : parameters) {
    LogEvent(parameter);
  }

  std::string err;
  return workspace_.GetExecutor().Run(CMD_EXEC, parameters, output, &err);
}

bool CmdCommand::AbortInstall(const std::string& session,
                              std::string* output) const noexcept {
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-abandon");
  parameters.emplace_back(session);
  output->clear();
  std::string err;
  return workspace_.GetExecutor().Run(CMD_EXEC, parameters, output, &err);
}

void CmdCommand::SetPath(const char* path) { CMD_EXEC = path; }

}  // namespace deploy