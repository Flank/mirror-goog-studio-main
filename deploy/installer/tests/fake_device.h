#ifndef INSTALLER_FAKE_DEVICE_H
#define INSTALLER_FAKE_DEVICE_H

#include <memory>
#include "tools/base/deploy/installer/tests/fake_device.grpc.pb.h"
namespace deploy {

class FakeDevice {
 public:
  FakeDevice();

  // Notifies the device that a shell command has been performed
  void RecordCommand(const std::string& command);

  // Asks the device to interpret and execute the shell command
  int ExecuteCommand(const std::string& command);

  // Returns the uid of the given app, or zero if it is not known
  int GetAppUid(const std::string& package);

  // Returns whether the file exists on the device
  bool Exists(const std::string& path);

  // Returns the uid of the current device user
  int GetCurrentUid();

  // Changes the current user
  void ChangeUid(int uid);

 private:
  std::unique_ptr<FakeDeviceService::Stub> client_;
};

}  // namespace deploy

#endif  // INSTALLER_FAKE_DEVICE_H