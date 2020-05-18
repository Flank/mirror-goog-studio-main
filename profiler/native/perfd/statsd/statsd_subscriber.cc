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

#include "statsd/proto/shell_data.pb.h"
#include "utils/log.h"

using android::os::statsd::ShellData;
using profiler::Log;
using profiler::NonBlockingCommandRunner;
using std::string;

constexpr char const* kStatsdCommand = "/system/bin/cmd";
// The array of argument strings passed to execve(). It must include a null
// pointer at the end of the array.
constexpr char const* kStatsdArgs[] = {kStatsdCommand, "stats",
                                       "data-subscribe", nullptr};

namespace profiler {

StatsdSubscriber& StatsdSubscriber::Instance() {
  static StatsdSubscriber* instance =
      new StatsdSubscriber(new NonBlockingCommandRunner(kStatsdCommand, true));
  return *instance;
}

void StatsdSubscriber::SubscribeToPulledAtom(
    std::unique_ptr<PulledAtom> pulled_atom) {
  pulled_atoms_[pulled_atom->atom_id()] = std::move(pulled_atom);
}

void StatsdSubscriber::Run() {
  if (runner_->IsRunning()) {
    return;
  }
  // Build config proto from all subscribed pulled atoms.
  for (const auto& pair : pulled_atoms_) {
    pair.second->BuildConfig(subscription_.add_pulled());
  }
  uint32_t size = subscription_.ByteSize();
  if (size == 0) {
    return;
  }
  std::vector<uint8_t> buffer(abi_size_in_bytes_ + size);
  // First we write the proto size into the first [abi_size_in_bytes_] bytes.
  if (abi_size_in_bytes_ == sizeof(uint64_t)) {
    // ABI is 64-bit, write a 64-bit integer to fit the size.
    uint64_t size_in_64_bits = size;
    std::memcpy(buffer.data(), &size_in_64_bits, abi_size_in_bytes_);
  } else {
    std::memcpy(buffer.data(), &size, abi_size_in_bytes_);
  }
  // Then we write the proto content.
  subscription_.SerializeToArray(buffer.data() + abi_size_in_bytes_, size);

  if (!runner_->Run(kStatsdArgs, string(buffer.begin(), buffer.end()),
                    &callback_, nullptr)) {
    Log::E(Log::Tag::PROFILER, "Failed to run statsd command.");
    return;
  }
}

void StatsdSubscriber::Stop() {
  subscription_.clear_pushed();
  subscription_.clear_pulled();
  pulled_atoms_.clear();
  // Don't block on the callback thread as it might still be waiting for statsd
  // output.
  runner_->Kill(false);
}

void StatsdSubscriber::HandleOutput(int stdout_fd) {
  uint64_t size = 0;
  ShellData shell_data;
  std::vector<uint8_t> buffer;
  while (runner_->IsRunning()) {
    if (read(stdout_fd, &size, abi_size_in_bytes_) == -1) {
      Log::E(Log::Tag::PROFILER, "Failed to read statsd data size: %s.",
             strerror(errno));
      return;
    }
    if (size == 0) {
      continue;
    }
    buffer.resize(size);
    if (read(stdout_fd, buffer.data(), size) == -1) {
      Log::E(Log::Tag::PROFILER, "Failed to read statsd data: %s.",
             strerror(errno));
      return;
    }
    if (shell_data.ParseFromArray(buffer.data(), size)) {
      for (int i = 0; i < shell_data.atom_size(); ++i) {
        auto atom = shell_data.atom(i);
        auto search = pulled_atoms_.find(atom.pulled_case());
        if (search != pulled_atoms_.end()) {
          search->second->OnAtomReceived(atom);
        }
      }
    } else {
      Log::E(Log::Tag::PROFILER, "Failed to parse statsd data.");
    }
  }
}
}  // namespace profiler
