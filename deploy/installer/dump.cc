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

#include "tools/base/deploy/installer/dump.h"

#include <iostream>

#include <dirent.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/apk_archive.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/installer/package_manager.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {
void DumpCommand::ParseParameters(int argc, char** argv) {
  if (argc < 1) {
    return;
  }

  for (int i = 0; i < argc; ++i) {
    package_names_.emplace_back(argv[i]);
  }

  ready_to_run_ = true;
}

void DumpCommand::Run() {
  Phase p("Command Dump");

  proto::DumpResponse* response = new proto::DumpResponse();
  workspace_.GetResponse().set_allocated_dump_response(response);

  for (const std::string& package_name : package_names_) {
    proto::PackageDump* package_dump = response->add_packages();
    package_dump->set_name(package_name);

    if (!GetProcessIds(package_name, package_dump)) {
      response->set_status(proto::DumpResponse::ERROR_PROCESS_NOT_FOUND);
      response->set_failed_package(package_name);
      return;
    }

    if (!GetApks(package_name, package_dump)) {
      response->set_status(proto::DumpResponse::ERROR_PACKAGE_NOT_FOUND);
      response->set_failed_package(package_name);
      return;
    }
  }

  response->set_status(proto::DumpResponse::OK);
}

bool DumpCommand::GetApks(const std::string& package_name,
                          proto::PackageDump* package_dump) {
  // Retrieve apks for this package.
  auto apks_path = RetrieveApks(package_name);
  if (apks_path.size() == 0) {
    ErrEvent("Could not find apks for package: " + package_name);
    return false;
  }

  // Extract all apks.
  for (std::string& apk_path : apks_path) {
    Phase p2("processing APK");
    ApkArchive archive(apk_path);
    Dump dump = archive.ExtractMetadata();

    proto::ApkDump* apk_dump = package_dump->add_apks();
    apk_dump->set_absolute_path(apk_path);
    if (dump.cd != nullptr || dump.signature != nullptr) {
      std::string apk_file_name =
          std::string(strrchr(apk_path.c_str(), '/') + 1);
      apk_dump->set_name(apk_file_name);
    }
    if (dump.cd != nullptr) {
      apk_dump->set_allocated_cd(dump.cd.release());
    }
    if (dump.signature != nullptr) {
      apk_dump->set_allocated_signature(dump.signature.release());
    }
  }

  return true;
}

bool DumpCommand::GetProcessIds(const std::string& package_name,
                                proto::PackageDump* package_dump) {
  Phase p("get process ids");

  std::string output;
  std::string error;
  if (!workspace_.GetExecutor().RunAs("id", package_name, {"-u"}, &output,
                                      &error)) {
    ErrEvent("Could not get package user id: " + error);
    return false;
  }

  int package_uid = strtol(output.c_str(), nullptr, 10);
  if (package_uid < 0) {
    ErrEvent("Could not parse package user id: " + output);
    return false;
  }

  DIR* proc_dir = opendir("/proc");
  if (proc_dir == nullptr) {
    ErrEvent("Could not open system /proc directory");
    return false;
  }

  int zygote_pid = 0;
  int zygote64_pid = 0;
  std::vector<ProcStats> stats_list;

  // Search the /proc directory for processes with a uid equal to the package
  // uid, as well as for the zygote and zygote64 processes.
  dirent* proc_entry;
  while ((proc_entry = readdir(proc_dir)) != nullptr) {
    // Skip entries that aren't integers.
    int pid = strtol(proc_entry->d_name, nullptr, 10);
    if (pid <= 0) {
      continue;
    }

    // Try to parse this process. If we fail, just continue to the next one.
    ProcStats stats;
    if (!ParseProc(proc_entry, &stats)) {
      continue;
    }

    // We obtain the zygote pids to allow us to filter out any non-ART
    // processes, as well as to determine whether each process is 32 or 64
    // bit.
    if (strcmp(stats.name, "zygote") == 0) {
      zygote_pid = stats.pid;
    } else if (strcmp(stats.name, "zygote64") == 0) {
      zygote64_pid = stats.pid;
    } else if (stats.uid == package_uid) {
      stats_list.emplace_back(stats);
    }
  }

  closedir(proc_dir);

  // If we haven't found any zygote processes, we can't tell what is an ART
  // process and what isn't, so we should exit early.
  if (zygote_pid == 0 && zygote64_pid == 0) {
    ErrEvent("Could not find a zygote process");
    return false;
  }

  for (auto& stats : stats_list) {
    // We assume an app can't mix 32-bit and 64-bit ART processes, so we just
    // set this to the last architecture of the last zygote child we find.
    if (stats.ppid == zygote_pid) {
      package_dump->set_arch(proto::PackageDump::ARCH_32_BIT);
    } else if (stats.ppid == zygote64_pid) {
      package_dump->set_arch(proto::PackageDump::ARCH_64_BIT);
    } else {
      continue;
    }

    package_dump->add_processes(stats.pid);
  }

  return true;
}

bool DumpCommand::ParseProc(dirent* proc_entry, ProcStats* stats) {
  std::string proc_path("/proc/");
  proc_path += proc_entry->d_name;

  struct stat proc_dir_stat;
  if (stat(proc_path.c_str(), &proc_dir_stat) < 0) {
    return false;
  }

  stats->uid = proc_dir_stat.st_uid;

  std::string cmdline_path = proc_path + "/cmdline";
  FILE* proc_cmdline = fopen(cmdline_path.c_str(), "r");
  if (proc_cmdline == nullptr) {
    return false;
  }

  // Read up to 15 characters + the null terminator. Processes may have an
  // empty command line, so we don't need to check that this succeeds.
  fscanf(proc_cmdline, "%15s", stats->name);
  fclose(proc_cmdline);

  std::string stat_path = proc_path + "/stat";
  FILE* proc_stat = fopen(stat_path.c_str(), "r");
  if (proc_stat == nullptr) {
    return false;
  }

  // The format of this string is well-specified by man proc(5).
  int parsed = fscanf(proc_stat, "%d %*s %*c %d", &stats->pid, &stats->ppid);
  fclose(proc_stat);

  if (parsed != 2) {
    return false;
  }

  return true;
}

std::vector<std::string> DumpCommand::RetrieveApks(
    const std::string& package_name) {
  Phase p("retrieve_apk_path");
  std::vector<std::string> apks;
  // First try with cmd. It may fail since path capability was added to "cmd" in
  // Android P.
  CmdCommand cmd(workspace_);
  std::string error_output;
  cmd.GetAppApks(package_name, &apks, &error_output);
  if (apks.size() == 0) {
    // "cmd" likely failed. Try with PackageManager (pm)
    PackageManager pm(workspace_);
    pm.GetApks(package_name, &apks, &error_output);
  }
  return apks;
}

}  // namespace deploy
