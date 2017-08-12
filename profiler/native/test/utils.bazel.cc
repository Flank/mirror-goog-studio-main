/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <string>

#include "test/utils.h"

namespace profiler {

std::string TestUtils::getCpuTestData(const std::string &path) {
  return "tools/base/profiler/native/testdata/perfd/cpu/" + path;
}

std::string TestUtils::getUtilsTestData(const std::string &path) {
  return "tools/base/profiler/native/testdata/utils/" + path;
}

std::string TestUtils::getNetworkTestData(const std::string &path) {
  return "tools/base/profiler/native/testdata/perfd/network/" + path;
}

std::string TestUtils::getMemoryTestData(const std::string &path) {
  return "tools/base/profiler/native/testdata/perfd/memory/" + path;
}

std::string TestUtils::getGraphicsTestData(const std::string &path) {
  return "tools/base/profiler/native/testdata/perfd/graphics/" + path;
}

}  // namespace profiler
