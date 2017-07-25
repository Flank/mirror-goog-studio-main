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

#include "utils/device_info.h"

#include <cstdlib>
#include <sstream>

using std::string;

namespace {

const char* const kGetpropCmd = "cat";
const char* const kPropFile = "./device_info.prop";
const char* const kCodeName = "ro.build.version.codename";
const char* const kRelease = "ro.build.version.release";
const char* const kSdk = "ro.build.version.sdk";
// API codename of a release (non preview) system image or platform.
const char* const kCodeNameRelease = "REL";

}  // namespace

namespace profiler {

DeviceInfo::DeviceInfo()
    : getprop_(kGetpropCmd),
      code_name_(GetSystemProperty(kCodeName)),
      release_(GetSystemProperty(kRelease)),
      sdk_(atoi(GetSystemProperty(kSdk).c_str())),
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
  string output;
  // For the host version of DeviceInfo we read the arguments from a file
  // located in the same directory as the binary. This allows us to control
  // the device properties externally as required for test.
  if (getprop_.Run(kPropFile, &output)) {
    std::istringstream properties(output);
    string property;
    string value;
    while (getline(properties, property)) {
      if (property.compare(0, property_name.length(), property_name) == 0) {
        value = property.substr(property_name.length()+1);
        break;
      }
    }
    return value;
  }
  return "";
}

}  // namespace profiler
