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

#include <dlfcn.h>
#include <sys/types.h>
#include <unistd.h>

#include <sstream>

#include "utils/log.h"
#include "utils/native_backtrace.h"

namespace profiler {

void SignalHandlerSigSegv(int signal) {
  const int MAX_SIZE = 20;
  std::vector<uintptr_t> stack = GetBacktrace(MAX_SIZE);
  // printf so this can be printed on a single line.
  std::stringstream stringify;
  stringify << "Perfd Segmentation Fault: ";
  for (int i = 0; i < stack.size(); i++) {
    Dl_info info;
    if (dladdr(reinterpret_cast<void*>(stack[i]), &info)) {
      // This line attempts to conver a virtual pointer to a program counter
      // address having a PC address allows us to run addr2line resolving the
      // stack symbol.
      stringify << stack[i] - reinterpret_cast<uintptr_t>(info.dli_fbase);
    } else {
      stringify << stack[i];
    }
    stringify << ",";
  }
  printf("%s\n", stringify.str().c_str());
  Log::E(Log::Tag::TRANSPORT, "%s", stringify.str().c_str());
  // Force flush output.
  fflush(stdout);

  // Set the signal back to the default signal hanlder.
  std::signal(signal, SIG_DFL);
  std::raise(signal);
}

extern "C" void SignalHandlerSigHup(int signal) {
  std::signal(signal, SIG_DFL);
  Log::D(Log::Tag::TRANSPORT, "Profiler:Signal received %d", signal);
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
  Log::D(Log::Tag::TRANSPORT,
         "Profiler:TerminationService shutting down with signal %d", signal);
  for (auto cb : shutdown_callbacks_) {
    cb(signal);
  }
}

}  // namespace profiler
