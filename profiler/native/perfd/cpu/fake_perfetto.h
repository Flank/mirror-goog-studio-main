/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef PERFD_CPU_FAKE_PERFETTO_H_
#define PERFD_CPU_FAKE_PERFETTO_H_

#include "perfetto.h"

namespace profiler {

// A subclass of Perfetto to be used in tests. The class maintains a simple
// state of if perfetto is assumed to be running or not.
class FakePerfetto : public Perfetto {
 public:
  explicit FakePerfetto() : running_state_(false) {}
  ~FakePerfetto() override {}

  void Run(const PerfettoArgs& run_args) override { running_state_ = true; }
  bool IsPerfettoRunning() override { return running_state_; }
  void Stop() override { running_state_ = false; }

 private:
  bool running_state_;
};

}  // namespace profiler

#endif  // PERFD_CPU_FAKE_PERFETTO_H_
