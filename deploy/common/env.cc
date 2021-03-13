/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "tools/base/deploy/common/env.h"

#include <stdlib.h>

namespace deploy {

namespace {
bool init_ = false;
int port_;
std::string root_;
std::string logcat_;
std::string shell_;
std::string build_type_;
int api_level_;
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

  char* api_level = getenv("FAKE_DEVICE_API_LEVEL");
  api_level_ = api_level == nullptr ? 21 : atoi(api_level);

  char* build_type = getenv("FAKE_BUILD_TYPE");
  build_type_ = build_type == nullptr ? "" : build_type;

  char* uid = getenv("FAKE_DEVICE_UID");
  uid_ = uid == nullptr ? 0 : atoi(uid);

  init_ = true;
}

}  // namespace

void Env::Reset() {
  init_ = false;
  Init();
}

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

std::string Env::build_type() {
  Init();
  return build_type_;
}

int Env::api_level() {
  Init();
  return api_level_;
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
