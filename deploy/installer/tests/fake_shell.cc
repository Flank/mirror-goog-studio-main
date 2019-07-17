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

  return device.ExecuteCommand(cmd);
}
