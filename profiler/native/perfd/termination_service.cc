/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "perfd/termination_service.h"

#include "utils/log.h"

namespace {

profiler::TerminationService* g_termination_service_ = nullptr;

}  // namespace

namespace profiler {

extern "C" void SignalHandler(int signal) {
  std::signal(signal, SIG_DFL);
  Log::D("Profiler:Signal received %d", signal);
  if (g_termination_service_ != nullptr) {
    TerminationService::GetTerminationService()->NotifyShutdown(signal);
  }
  std::raise(signal);
}

TerminationService* TerminationService::GetTerminationService() {
  if (g_termination_service_ == nullptr) {
    g_termination_service_ = new TerminationService();
  }
  return g_termination_service_;
}

TerminationService::TerminationService() { std::signal(SIGHUP, SignalHandler); }

void TerminationService::NotifyShutdown(int signal) {
  Log::D("Profiler:TerminationService shutting down with signal %d", signal);
  for (auto cb : shutdown_callbacks_) {
    cb(signal);
  }
}

}  // namespace profiler
