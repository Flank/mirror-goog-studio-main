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
#include "utils/termination_service.h"

#include <sys/types.h>
#include <unistd.h>
#include <sstream>
#include "utils/log.h"
#include "utils/native_backtrace.h"

namespace profiler {

void SignalHandlerSigSegv(int signal) {
  const int MAX_SIZE = 20;
  std::vector<std::uintptr_t> stack = GetBacktrace(MAX_SIZE);
  // printf so this can be printed on a single line.
  printf("Perfd Segmentation Fault: ");
  std::stringstream stringify;
  for (int i = 0; i < stack.size(); i++) {
    stringify << stack[i] << ",";
  }
  printf("%s\n", stringify.str().c_str());
  // Force flush output.
  fflush(stdout);

  // Set the signal back to the default signal hanlder.
  std::signal(signal, SIG_DFL);
  std::raise(signal);
}

extern "C" void SignalHandlerSigHup(int signal) {
  std::signal(signal, SIG_DFL);
  Log::D("Profiler:Signal received %d", signal);
  TerminationService::Instance()->NotifyShutdown(signal);
  std::raise(signal);
}

TerminationService* TerminationService::Instance() {
  static TerminationService* instance = new TerminationService();
  return instance;
}

TerminationService::TerminationService() {
  std::signal(SIGHUP, SignalHandlerSigHup);
  std::signal(SIGSEGV, SignalHandlerSigSegv);
}

void TerminationService::NotifyShutdown(int signal) {
  Log::D("Profiler:TerminationService shutting down with signal %d", signal);
  for (auto cb : shutdown_callbacks_) {
    cb(signal);
  }
}

}  // namespace profiler
