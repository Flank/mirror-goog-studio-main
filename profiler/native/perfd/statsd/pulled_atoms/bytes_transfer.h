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
#ifndef PERFD_STATSD_PULLED_ATOMS_BYTES_TRANSFER_H_
#define PERFD_STATSD_PULLED_ATOMS_BYTES_TRANSFER_H_

#include <memory>
#include <utility>

#include "daemon/event_buffer.h"
#include "proto/network.pb.h"
#include "pulled_atom.h"
#include "utils/clock.h"
#include "utils/speed_converter.h"
#include "utils/time_value_buffer.h"

namespace profiler {

// Base class for WifiBytesTransfer and MobileBytesTransfer shared logic.
class BytesTransfer : public PulledAtom {
 public:
  BytesTransfer(int32_t pid, int32_t uid, Clock* clock,
                EventBuffer* event_buffer)
      : pid_(pid),
        uid_(uid),
        clock_(clock),
        event_buffer_(event_buffer),
        legacy_buffer_(nullptr),
        tx_converter_(nullptr),
        rx_converter_(nullptr) {}
  ~BytesTransfer() = default;

  void BuildConfig(
      android::os::statsd::PulledAtomSubscription* pulled) override;
  void OnAtomReceived(const android::os::statsd::Atom& atom) override;

  int32_t pid() { return pid_; }
  // In the legacy pipeline, network profiler uses its own data buffer and
  // initializes it in StartMonitoringApp call. So we need to expose a method
  // to dynamically set and reset it.
  void SetLegacyBuffer(
      TimeValueBuffer<profiler::proto::NetworkProfilerData>* legacy_buffer) {
    legacy_buffer_ = legacy_buffer;
  }

 protected:
  // Id of the uid field in the atom, for filtering atoms.
  // To be implemented by concrete classes.
  virtual int32_t uid_field_id() = 0;
  // Extracts received and sent bytes from the received atom.
  // To be implemented by concrete classes.
  virtual std::pair<int64_t, int64_t> ExtractBytes(
      const android::os::statsd::Atom& atom) = 0;

 private:
  const int32_t kFreqMillis = 1000;

  int32_t pid_;
  uint32_t uid_;
  Clock* clock_;
  // For unified pipeline.
  EventBuffer* event_buffer_;
  // For legacy pipeline.
  TimeValueBuffer<profiler::proto::NetworkProfilerData>* legacy_buffer_;
  std::unique_ptr<SpeedConverter> tx_converter_;
  std::unique_ptr<SpeedConverter> rx_converter_;
};
}  // namespace profiler

#endif  // PERFD_STATSD_PULLED_ATOMS_BYTES_TRANSFER_H_
