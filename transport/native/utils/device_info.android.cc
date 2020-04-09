/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <cstdlib>

#include <sys/system_properties.h>

#include "utils/device_info.h"
#include "utils/trace.h"

using std::string;

namespace {

const char* const kGetpropCmd = "/system/bin/getprop";
const char* const kSerial = "ro.serialno";
const char* const kCodeName = "ro.build.version.codename";
const char* const kRelease = "ro.build.version.release";
const char* const kSdk = "ro.build.version.sdk";
const char* const kBuildType = "ro.build.type";
const char* const kCharacteristics = "ro.build.characteristics";
const char* const kAbi = "ro.product.cpu.abi";
// API codename of a release (non preview) system image or platform.
const char* const kCodeNameRelease = "REL";

// Returns the value of the system property of the given |key|, or the
// |default_value| if the property is unavailable.
//
// __system_property_read() has been deprecated since API level 26 (O) because
// it works only on property whose name is shorter than 32 char (PROP_NAME_MAX)
// and value is shorter than 92 char (PROP_VALUE_MAX).
// __system_property_read_callback() is recommended since then but it's not
// availale for API < 26. The length limits on the name and value are not a
// concern for us. Therefore, we still use __system_property_read() for
// simplicity.
string GetProperty(const string& key, const string& default_value) {
  string property_value;
  const prop_info* pi = __system_property_find(key.c_str());
  if (pi == nullptr) return default_value;

  char name[PROP_NAME_MAX];
  char value[PROP_VALUE_MAX];

  if (__system_property_read(pi, name, value) == 0) return default_value;
  return value;
}

}  // namespace

namespace profiler {

DeviceInfo::DeviceInfo()
    : getprop_(kGetpropCmd),
      serial_(GetSystemProperty(kSerial)),
      code_name_(GetSystemProperty(kCodeName)),
      release_(GetSystemProperty(kRelease)),
      sdk_(atoi(GetSystemProperty(kSdk).c_str())),
      is_user_build_(GetSystemProperty(kBuildType) == "user"),
      is_emulator_(GetSystemProperty(kCharacteristics).find("emulator") !=
                   string::npos),
      is_64_bit_abi_(IsAbi64Bit(GetSystemProperty(kAbi))),
      // If the codename property is empty or a known fixed string ("REL"),
      // it is a release system image and feature level is the same as sdk
      // level. Otherwise, it is a preview impage, and feature level is sdk
      // level + 1.
      feature_level_((code_name_.empty() || code_name_ == kCodeNameRelease)
                         ? sdk_
                         : sdk_ + 1) {}

DeviceInfo* DeviceInfo::Instance() {
  static DeviceInfo* instance = new DeviceInfo();
  return instance;
}

string DeviceInfo::GetSystemProperty(const string& property_name) const {
  Trace trace("GetSystemProperty");
  return GetProperty(property_name, "");
}

}  // namespace profiler
