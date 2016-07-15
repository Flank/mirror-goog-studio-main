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
#ifndef UTILS_MOCK_CLOCK_H_
#define UTILS_MOCK_CLOCK_H_

#include "utils/clock.h"

namespace profiler {

// A mock implementation of |Clock|, useful for tests
class MockClock final : public Clock {
 public:
  MockClock(int64_t mockTime = 0) : mockTime_(mockTime) {}

  virtual int64_t GetCurrentTime() const override { return mockTime_; }

  void SetCurrentTime(int64_t time) { mockTime_ = time; }

  void Elapse(int64_t elapsed) { mockTime_ += elapsed; }

 private:
  int64_t mockTime_;
};

}  // namespace profiler

#endif  // UTILS_MOCK_CLOCK_H_
