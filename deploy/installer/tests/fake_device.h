#ifndef INSTALLER_FAKE_DEVICE_H
#define INSTALLER_FAKE_DEVICE_H

#include <memory>
#include "tools/base/deploy/installer/tests/fake_device.grpc.pb.h"
namespace deploy {

class FakeDevice {
 public:
  FakeDevice();

  // Asks the device to interpret and execute the shell command
  int ExecuteCommand(const std::string& command);

 private:
  std::unique_ptr<FakeDeviceService::Stub> client_;
};

}  // namespace deploy

#endif  // INSTALLER_FAKE_DEVICE_H