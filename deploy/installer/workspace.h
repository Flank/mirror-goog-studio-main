#ifndef INSTALLER_WORKSPACE_H
#define INSTALLER_WORKSPACE_H

#include <dirent.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <iostream>
#include <string>

namespace deployer {

class Workspace {
 public:
  Workspace(const std::string& executable_path)
      : executable_path_(executable_path) {}

  std::string GetBase() const noexcept {
    // Retrieves the base folder which is expected to be ".studio" somewhere in the
    // path.e.g: /data/local/tmp/.studio/bin base is /data/local/tmp/.studio.
    char* directory_cursor = const_cast<char*>(executable_path_.c_str());
    // Search for ".studio" folder.
    while (directory_cursor[0] != '/' || directory_cursor[1] != 0) {
      directory_cursor = dirname(directory_cursor);
      if (!strcmp(kBasename, basename(directory_cursor))) {
        return directory_cursor;
      }
    }
    std::cerr << "Unable to find '" << kBasename << "' base folder in '"
              << executable_path_ << "'" << std::endl;
    return "";
  }

  void ClearDirectory(const char* dirname) const noexcept {
    DIR* dir;
    struct dirent* entry;
    char path[PATH_MAX];
    dir = opendir(dirname);
    while ((entry = readdir(dir)) != NULL) {
      if (strcmp(entry->d_name, ".") && strcmp(entry->d_name, "..")) {
        snprintf(path, (size_t)PATH_MAX, "%s/%s", dirname, entry->d_name);
        if (entry->d_type == DT_DIR) {
          ClearDirectory(path);
        }
        unlink(path);
      }
    }
    closedir(dir);
  }

 private:
  static constexpr auto kBasename = ".studio";
  std::string executable_path_;
};

}  // namespace deployer

#endif