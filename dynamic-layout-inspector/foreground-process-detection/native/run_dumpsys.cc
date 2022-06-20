/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "foreground_process_tracker.h"

#include <string>

namespace layout_inspector {

// Real implementation of |runDumpsysCommand|, as oppsed to
// |mock_run_dumpsys.cc|. The separation is needed to be able to mock the method
// when running on fakeandroid, which doesn't have dumpsys.
ProcessInfo ForegroundProcessTracker::runDumpsysTopActivityCommand() {
  std::string dumpsysTopActivityOutput;
  dumpsysTopActivityCommandRunner_->Run("", &dumpsysTopActivityOutput);
  return parseProcessInfo(dumpsysTopActivityOutput);
}

// Runs dumpsys to check if we can detect sleeping Activities
bool ForegroundProcessTracker::hasSleepingActivities() {
  std::string sleepingActivitiesOutput;
  dumpsysSleepingActivitiesCommandRunner_->Run("", &sleepingActivitiesOutput);
  return !sleepingActivitiesOutput.empty();
}

// Runs dumpsys to check if we can detect awake Activities
bool ForegroundProcessTracker::hasAwakeActivities() {
  std::string awakeActivitiesOutput;
  dumpsysAwakeActivitiesCommandRunner_->Run("", &awakeActivitiesOutput);
  return !awakeActivitiesOutput.empty();
}

}  // namespace layout_inspector