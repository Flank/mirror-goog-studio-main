#include "package_manager.h"
#include <cstring>
#include <iostream>
#include <sstream>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

namespace {
const char* PM_EXEC = "/system/bin/pm";
}  // namespace

bool PackageManager::GetApks(const std::string& package_name,
                             std::vector<std::string>* apks,
                             std::string* error_string) const {
  Phase p("PackageManager::GetAppBaseFolder");
  std::vector<std::string> parameters;
  parameters.emplace_back("path");
  parameters.emplace_back(package_name);
  std::string out;
  std::string err;
  bool success = workspace_.GetExecutor().Run(PM_EXEC, parameters, &out, &err);
  if (!success) {
    *error_string = err;
    return false;
  }
  // pm returns the path to the apk. We need to parse the response:
  // package:/data/app/net.fabiensanglard.shmup-1/base.apk
  // into
  // /data/app/net.fabiensanglard.shmup-1
  //  Make sure input is well-formed.
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
void PackageManager::SetPath(const char* path) { PM_EXEC = path; }

}  // namespace deploy