#include <stdio.h>
#include <unistd.h>
#include <string>
#include <vector>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/installer/tests/fake_device.h"

using deploy::Env;
using deploy::FakeDevice;

int main(int argc, char* argv[]) {
  if (argc < 2) {
    printf(
        "run-as: usage: run-as <package-name> [--user <uid>] <command> "
        "[<args>]\n");
    return 1;
  }
  if (!Env::IsValid()) {
    return 127;
  }

  FakeDevice device;
  int uid = device.GetAppUid(argv[1]);
  if (uid == 0) {
    printf("run-as: Package '%s' is unknown\n", argv[1]);
    return 1;
  }

  const char** new_argv = new const char*[argc];
  std::string shell = Env::shell();
  new_argv[0] = shell.c_str();
  for (int i = 2; i < argc; i++) {
    new_argv[i - 1] = argv[i];
  }
  new_argv[argc - 1] = nullptr;
  Env::set_uid(uid);
  int ret = execvp(new_argv[0], (char* const*)new_argv);
  delete[] new_argv;
  return ret;
}