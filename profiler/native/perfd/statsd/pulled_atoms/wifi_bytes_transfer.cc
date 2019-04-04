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
#include "wifi_bytes_transfer.h"

using android::os::statsd::Atom;
using android::os::statsd::PulledAtomSubscription;

namespace profiler {

void WifiBytesTransfer::BuildConfig(PulledAtomSubscription* pulled) {
  auto* matcher = pulled->mutable_matcher();
  matcher->set_atom_id(AtomId());
  auto* field_value_matcher = matcher->add_field_value_matcher();
  field_value_matcher->set_field(
      android::os::statsd::WifiBytesTransfer::kUidFieldNumber);
  field_value_matcher->set_eq_int(uid_);
  pulled->set_freq_millis(kFreqMillis);
}

void WifiBytesTransfer::OnAtomReceived(const Atom& atom) {
  std::lock_guard<std::mutex> lock(data_mutex_);
  has_data_ = true;
  auto wifi_bytes_transfer = atom.wifi_bytes_transfer();
  rx_bytes_ = wifi_bytes_transfer.rx_bytes();
  tx_bytes_ = wifi_bytes_transfer.tx_bytes();
}

bool WifiBytesTransfer::has_data() {
  std::lock_guard<std::mutex> lock(data_mutex_);
  return has_data_;
}
}  // namespace profiler
