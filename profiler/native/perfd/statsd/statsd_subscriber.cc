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
#include "statsd_subscriber.h"

#include <stdio.h>
#include <unistd.h>
#include <cstddef>
#include <functional>
#include <memory>
#include <vector>

#include "proto/statsd/shell_data.pb.h"
#include "utils/log.h"

using android::os::statsd::ShellData;
using profiler::Log;
using profiler::NonBlockingCommandRunner;
using std::string;

constexpr char const* kStatsdCommand = "/system/bin/cmd";
constexpr char const* kStatsdArgs[] = {kStatsdCommand, "stats",
                                       "data-subscribe"};

namespace profiler {

StatsdSubscriber& StatsdSubscriber::Instance() {
  static StatsdSubscriber* instance =
      new StatsdSubscriber(new NonBlockingCommandRunner(kStatsdCommand));
  return *instance;
}

void StatsdSubscriber::SubscribeToPulledAtom(
    std::unique_ptr<PulledAtom> pulled_atom) {
  pulled_atoms_[pulled_atom->AtomId()] = std::move(pulled_atom);
}

void StatsdSubscriber::Run() {
  if (runner_->IsRunning()) {
    return;
  }
  // Build config proto from all subscribed pulled atoms.
  for (const auto& pair : pulled_atoms_) {
    pair.second->BuildConfig(subscription_.add_pulled());
  }
  size_t size = subscription_.ByteSize();
  if (size == 0) {
    return;
  }
  std::vector<char> buffer(sizeof(size) + size);
  // First we write the proto size into the first few bytes.
  for (size_t i = 0; i < sizeof(size); ++i) {
    buffer[i] = size >> (i * 8);
  }
  // Then we write the proto content.
  subscription_.SerializeToArray(buffer.data() + sizeof(size), size);

  if (!runner_->Run(kStatsdArgs, string(buffer.begin(), buffer.end()),
                    &callback_)) {
    Log::E("Failed to run statsd command.");
    return;
  }
}

void StatsdSubscriber::Stop() {
  subscription_.clear_pushed();
  subscription_.clear_pulled();
  pulled_atoms_.clear();
  runner_->Kill();
}

void StatsdSubscriber::HandleOutput(int stdout_fd) {
  size_t size = 0;
  ShellData shell_data;
  std::vector<uint8_t> buffer;
  while (runner_->IsRunning()) {
    if (read(stdout_fd, &size, sizeof(size)) == -1) {
      Log::E("Failed to read statsd data size: %s.", strerror(errno));
      return;
    }
    if (size == 0) {
      continue;
    }
    buffer.resize(size);
    if (read(stdout_fd, buffer.data(), size) == -1) {
      Log::E("failed to read statsd data: %s.", strerror(errno));
      return;
    }
    if (shell_data.ParseFromArray(buffer.data(), size)) {
      for (int i = 0; i < shell_data.atom_size(); ++i) {
        auto atom = shell_data.atom(i);
        auto search = pulled_atoms_.find(atom.pulled_case());
        if (search != pulled_atoms_.end()) {
          search->second->OnAtomRecieved(atom);
        }
      }
    } else {
      Log::E("Failed to parse statsd data.");
    }
  }
}
}  // namespace profiler
