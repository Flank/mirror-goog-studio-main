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
#ifndef PERFD_STATSD_PULLED_ATOMS_WIFI_BYTES_TRANSFER_H_
#define PERFD_STATSD_PULLED_ATOMS_WIFI_BYTES_TRANSFER_H_

#include "bytes_transfer.h"

namespace profiler {

// A pulled atom that contains bytes transferred over WiFi since device boot.
class WifiBytesTransfer : public BytesTransfer {
 public:
  WifiBytesTransfer(int32_t pid, int32_t uid, Clock* clock,
                    EventBuffer* event_buffer)
      : BytesTransfer(pid, uid, clock, event_buffer) {}

  int32_t atom_id() override {
    return android::os::statsd::Atom::PulledCase::kWifiBytesTransfer;
  }

 protected:
  int32_t uid_field_id() override {
    return android::os::statsd::WifiBytesTransfer::kUidFieldNumber;
  }
  std::pair<int64_t, int64_t> ExtractBytes(
      const android::os::statsd::Atom& atom) override {
    auto wifi_bytes_transfer = atom.wifi_bytes_transfer();
    return std::make_pair(wifi_bytes_transfer.tx_bytes(),
                          wifi_bytes_transfer.rx_bytes());
  }
};
}  // namespace profiler

#endif  // PERFD_STATSD_PULLED_ATOMS_WIFI_BYTES_TRANSFER_H_
