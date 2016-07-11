#include "app_base.h"

#include <libgen.h>
#include <limits.h>
#include <stdlib.h>
#include <unistd.h>

namespace profiler {

AppBase* AppBase::Instance() {
  static AppBase *instance = new AppBase();
  return instance;
}

void AppBase::SetBase(const char* executing_path) {
  char buf[PATH_MAX + 1];
  buf[PATH_MAX] = '\0';

  // Populate buf with unresolved base path.
  if (executing_path[0] == '/') {  // perfd exec-ed with absolute path
    // If perfd was called with an absolute path /X/Y/Z/perfd, we only need to
    // remove the executable name to find the base.
    strncpy(buf, executing_path, sizeof(buf));
  } else {
    // Perfd was execed with a path relative to cwd:
    char* cwd = getcwd(NULL, 0);
    std::string full_path = cwd;
    full_path += "/";
    full_path += executing_path;
    strncpy(buf, full_path.c_str(), sizeof(buf));
    buf[PATH_MAX] = '\0';
    free(cwd);
  }
  dir_ = dirname(buf);

  // Final step: Resolve the path to remove ".", ".." and symbolic paths.
  dir_ = realpath(dir_.c_str(), buf);
  dir_ += "/";
}

const std::string& AppBase::GetBase() { return dir_; }
}
