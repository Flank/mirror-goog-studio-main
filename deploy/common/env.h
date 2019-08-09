#ifndef COMMON_ENV_H
#define COMMON_ENV_H

#include <string>

namespace deploy {

// The global environment we are running on. In production this is invalid,
// but when running tests it represents the test enviroment.
class Env {
 public:
  // Whether there is a custom environment set
  static bool IsValid();

  // A port where to communicate with a FakeDevice grpc server
  static int port();

  // Where the root folder is located. Empty in production.
  static std::string root();

  // The file where to save logcat to.
  static std::string logcat();

  // The shell binary to use when invoking commands.
  static std::string shell();

  // The API level of the current device.
  static int api_level();

  // The uid of the android system. (Not the same as the actual running uid)
  static int uid();

  // Changes the current uid of the android system. No effect in production.
  static void set_uid(int uid);
};

}  // namespace deploy

#endif  // COMMON_ENV_H