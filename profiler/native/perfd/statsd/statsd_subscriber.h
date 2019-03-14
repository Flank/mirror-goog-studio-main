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
#ifndef PERFD_STATSD_STATSD_SUBSCRIBER_H_
#define PERFD_STATSD_STATSD_SUBSCRIBER_H_

#include <sys/types.h>
#include <memory>
#include <string>
#include <type_traits>
#include <unordered_map>

#include "proto/statsd/shell_config.pb.h"
#include "pulled_atoms/pulled_atom.h"
#include "utils/nonblocking_command_runner.h"

namespace profiler {

class StatsdSubscriber {
 public:
  // Visible for testing.
  // Production code should use StatsdSubscriber::Instance().
  StatsdSubscriber(NonBlockingCommandRunner* runner) : runner_(runner) {}
  ~StatsdSubscriber() { Stop(); }

  // Singleton class (except for test code).
  static StatsdSubscriber& Instance();

  // Subscribe to a statsd pulled atom. Atoms of the same atom ID can only be
  // subscribed once. To subscribe to multiple instances of the same atom, use
  // multiple atom matchers in the implementation of the provided PulledAtom.
  // Not thread safe.
  void SubscribeToPulledAtom(std::unique_ptr<profiler::PulledAtom> pulled_atom);
  // Start running the Android stats command to collect system events.
  // Not thread safe.
  void Run();
  // Stop the stats command and atom collection.
  // Not thread safe.
  void Stop();
  // Find statsd atom by atom_id, as defined in atoms.proto.
  // Returns null if not found.
  // Template parameter T must be a subclass of PulledAtom.
  template <class T, typename std::enable_if<std::is_base_of<
                         PulledAtom, T>::value>::type* = nullptr>
  T* FindAtom(int32_t atom_id) {
    auto search = pulled_atoms_.find(atom_id);
    if (search != pulled_atoms_.end()) {
      return dynamic_cast<T*>(search->second.get());
    }
    return nullptr;
  }

 private:
  // Use StatsSubscriber::Instance() to instantiate.
  StatsdSubscriber() = delete;

  // Bound to NonBlockingCommandRunner's callback thread.
  void HandleOutput(int stdout_fd);

  // Command runner to fork the stats command.
  NonBlockingCommandRunner* runner_;
  // ShellSubscription proto to send to the stats command.
  android::os::statsd::ShellSubscription subscription_;
  // Atom ID to PulledAtom mapping.
  std::unordered_map<int32_t, std::unique_ptr<profiler::PulledAtom>>
      pulled_atoms_;
};

}  // namespace profiler

#endif  // PERFD_STATSD_STATSD_SUBSCRIBER_H_
