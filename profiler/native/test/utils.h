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
#ifndef PROFILER_TEST_UTILS_H_
#define PROFILER_TEST_UTILS_H_

#include <string>

namespace profiler {

// Class to define methods used across all the native tests.
class TestUtils {
 public:
  // Returns the correct testdata path correspondent to perfd cpu package
  // given a relative path.
  static std::string getCpuTestData(const std::string &path);

  // Returns the correct testdata path correspondent to perfd network package
  // given a relative path.
  static std::string getNetworkTestData(const std::string &path);

  // Returns the correct testdata path correspondent to perfd memory package
  // given a relative path.
  static std::string getMemoryTestData(const std::string &path);

  // Returns the correct testdata path correspondent to perfd graphics package
  // given a relative path.
  static std::string getGraphicsTestData(const std::string &path);
};

}  // namespace profiler

#endif  // PROFILER_TEST_UTILS_H_