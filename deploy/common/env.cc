#include "tools/base/deploy/common/env.h"

#include <stdlib.h>

namespace deploy {

namespace {
bool init_ = false;
int port_;
std::string root_;
std::string logcat_;
std::string shell_;
int uid_;

void Init() {
  if (init_) {
    return;
  }
  char* port = getenv("FAKE_DEVICE_PORT");
  port_ = port == nullptr ? 0 : atoi(port);

  char* root = getenv("FAKE_DEVICE_ROOT");
  root_ = root == nullptr ? "" : root;

  char* logcat = getenv("FAKE_DEVICE_LOGCAT");
  logcat_ = logcat == nullptr ? "" : logcat;

  char* shell = getenv("FAKE_DEVICE_SHELL");
  shell_ = shell == nullptr ? "" : shell;

  char* uid = getenv("FAKE_DEVICE_UID");
  uid_ = uid == nullptr ? 0 : atoi(uid);

  init_ = true;
}

}  // namespace
bool Env::IsValid() { return true; }

int Env::port() {
  Init();
  return port_;
}

std::string Env::root() {
  Init();
  return root_;
}

std::string Env::logcat() {
  Init();
  return logcat_;
}

std::string Env::shell() {
  Init();
  return shell_;
}

int Env::uid() {
  Init();
  return uid_;
}

void Env::set_uid(int uid) {
  std::string s = std::to_string(uid);
  setenv("FAKE_DEVICE_UID", s.c_str(), true);
  uid_ = uid;
}

}  // namespace deploy
