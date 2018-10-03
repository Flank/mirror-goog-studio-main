#include "package_manager.h"
#include <cstring>
#include <iostream>
#include <sstream>

#include "trace.h"

namespace deploy {

namespace {
const char* PM_EXEC = "/system/bin/pm";
}  // namespace

PackageManager::PackageManager() : ShellCommandRunner(PM_EXEC) {}

bool PackageManager::GetApks(const std::string& package_name,
                             std::vector<std::string>* apks,
                             std::string* error_string) const {
  Trace trace("PackageManager::GetAppBaseFolder");
  std::string parameters;
  parameters.append("path ");
  parameters.append(package_name);
  std::string output;
  bool success = Run(parameters, &output);
  if (!success) {
    *error_string = output;
    return false;
  }
  // pm returns the path to the apk. We need to parse the response:
  // package:/data/app/net.fabiensanglard.shmup-1/base.apk
  // into
  // /data/app/net.fabiensanglard.shmup-1
  //  Make sure input is well-formed.
  std::stringstream ss(output);
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