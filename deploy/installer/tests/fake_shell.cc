#include <unistd.h>
#include <string>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/installer/tests/fake_device.h"

using deploy::Env;
using deploy::FakeDevice;

// A fake shell implementation, it executes the command given as argument by
// asking the FakeDevice running on the test to do so. It then forwards all the
// stdin and stdout to the right places.
int main(int argc, char* argv[]) {
  if (argc < 2) {
    return 127;
  }
  FakeDevice device;
  if (!Env::IsValid()) {
    return 127;
  }

  char* executable = argv[1];
  std::string cmd = executable;
  for (int i = 2; i < argc; i++) {
    cmd += " ";
    cmd += argv[i];
  }

  // If the file exists on the fake file system, we exec to it and notify the
  // device we are doing so, otherwise we ask the device to execute it,
  int ret_value = 0;
  if (device.Exists(executable)) {
    const char** new_argv = new const char*[argc];
    std::string exe = Env::root() + executable;
    new_argv[0] = exe.c_str();
    for (int i = 2; i < argc; i++) {
      new_argv[i - 1] = argv[i];
    }
    new_argv[argc - 1] = nullptr;
    device.RecordCommand(cmd);
    ret_value = execvp(exe.c_str(), (char* const*)new_argv);
    delete[] new_argv;
  } else {
    ret_value = device.ExecuteCommand(cmd);
  }

  return ret_value;
}
