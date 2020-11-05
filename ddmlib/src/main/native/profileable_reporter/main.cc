/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <stdlib.h>

#include "ddmlib/src/main/native/profileable_reporter/detector.h"

using ddmlib::Detector;

int main(int argc, char** argv) {
  Detector::LogFormat format = Detector::LogFormat::kBinary;
  if (argc >= 2) {
    switch (argv[1][0]) {
      case 'd':
        format = Detector::LogFormat::kDebug;
        break;
      case 'h':
        format = Detector::LogFormat::kHuman;
        break;
      default:
        format = Detector::LogFormat::kBinary;
    }
  }
  Detector detector(format);
  detector.Detect();
  return 0;
}
