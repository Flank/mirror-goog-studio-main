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

#include <sys/types.h>

#include "pulled_atom.h"

namespace profiler {

// A pulled atom that contains bytes transfered over WiFi since device boot.
class WifiBytesTransfer : public PulledAtom {
 public:
  WifiBytesTransfer(uint32_t uid) : uid_(uid), rx_bytes_(0), tx_bytes_(0) {}

  virtual int32_t AtomId() override {
    return android::os::statsd::Atom::PulledCase::kWifiBytesTransfer;
  }
  virtual void BuildConfig(
      android::os::statsd::PulledAtomSubscription* pulled) override;
  virtual void OnAtomRecieved(const android::os::statsd::Atom& atom) override;

  int64_t rx_bytes() { return rx_bytes_; }
  int64_t tx_bytes() { return tx_bytes_; }
  uint32_t uid() { return uid_; }

 private:
  const int32_t kFreqMillis = 500;

  uint32_t uid_;
  int64_t rx_bytes_;
  int64_t tx_bytes_;
};
}  // namespace profiler

#endif  // PERFD_STATSD_PULLED_ATOMS_WIFI_BYTES_TRANSFER_H_
