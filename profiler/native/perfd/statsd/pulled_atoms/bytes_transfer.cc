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
#include "bytes_transfer.h"

#include "utils/log.h"

using android::os::statsd::Atom;
using android::os::statsd::PulledAtomSubscription;
using profiler::Log;
using profiler::proto::Event;
using profiler::proto::NetworkProfilerData;

namespace profiler {

void BytesTransfer::BuildConfig(PulledAtomSubscription* pulled) {
  pulled->set_freq_millis(kFreqMillis);
  auto* matcher = pulled->mutable_matcher();
  matcher->set_atom_id(atom_id());
  auto* field_value_matcher = matcher->add_field_value_matcher();
  field_value_matcher->set_field(uid_field_id());
  field_value_matcher->set_eq_int(uid_);
}

void BytesTransfer::OnAtomReceived(const Atom& atom) {
  int64_t time = clock_->GetCurrentTime();
  auto bytes = ExtractBytes(atom);
  int64_t tx_bytes = bytes.first;
  int64_t rx_bytes = bytes.second;
  if (tx_converter_.get() == nullptr) {
    tx_converter_.reset(new SpeedConverter(time, tx_bytes));
  } else {
    tx_converter_->Add(time, tx_bytes);
  }
  if (rx_converter_.get() == nullptr) {
    rx_converter_.reset(new SpeedConverter(time, rx_bytes));
  } else {
    rx_converter_->Add(time, rx_bytes);
  }
  auto tx_speed = tx_converter_->speed();
  auto rx_speed = rx_converter_->speed();
  if (event_buffer_ != nullptr) {
    Event tx_event;
    tx_event.set_pid(pid_);
    tx_event.set_group_id(Event::NETWORK_TX);
    tx_event.set_kind(Event::NETWORK_SPEED);
    auto speed = tx_event.mutable_network_speed();
    speed->set_throughput(tx_speed);
    event_buffer_->Add(tx_event);

    Event rx_event;
    rx_event.set_pid(pid_);
    rx_event.set_group_id(Event::NETWORK_RX);
    rx_event.set_kind(Event::NETWORK_SPEED);
    speed = rx_event.mutable_network_speed();
    speed->set_throughput(rx_speed);
    event_buffer_->Add(rx_event);
  } else if (legacy_buffer_ != nullptr) {
    NetworkProfilerData data;
    data.set_end_timestamp(time);
    auto speed_data = data.mutable_speed_data();
    speed_data->set_sent(tx_speed);
    speed_data->set_received(rx_speed);
    legacy_buffer_->Add(data, time);
  }
}
}  // namespace profiler
