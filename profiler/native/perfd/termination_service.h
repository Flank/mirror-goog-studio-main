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
#ifndef TERMINATION_SERVICE_H_
#define TERMINATION_SERVICE_H_

#include <csignal>
#include <functional>
#include <list>
#include <string>
#include <vector>

namespace profiler {

using ShutdownCallback = std::function<void(int signal)>;

// Process signalling takes a C-linkage handler of the following prototype.
extern "C" void SignalHandler(int signal);

// A service which handles process signalling, giving the process a chance
// to run shutdown code before finally being terminated.
class TerminationService {
 public:
  static TerminationService* GetTerminationService();

  void RegisterShutdownCallback(ShutdownCallback shutdown_callback) {
    shutdown_callbacks_.push_back(shutdown_callback);
  }

 private:
  std::list<ShutdownCallback> shutdown_callbacks_;

  TerminationService();

  // If we properly terminate, then we should notify all callbacks with
  // user-defined shutdown signal. This way, we only need to worry about
  // shutdown in one place.
  ~TerminationService() { NotifyShutdown(SIGUSR1); }

  void NotifyShutdown(int signal);

  // Friend C-style function to allow access to internal methods.
  friend void SignalHandler(int signal);
};

}  // namespace profiler

#endif  // TERMINATION_SERVICE_H_
