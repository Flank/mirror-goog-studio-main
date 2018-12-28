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
#include "network_speed_sampler.h"

#include "perfd/event_buffer.h"

#include "proto/common.pb.h"
#include "proto/network.pb.h"

namespace profiler {

using proto::Event;

void NetworkSpeedSampler::Sample() {
  int32_t pid = session().info().pid();
  speed_sampler_.Refresh();
  auto data = speed_sampler_.Sample(uid_);

  Event tx_event;
  tx_event.set_pid(pid);
  tx_event.set_group_id(Event::NETWORK_TX);
  tx_event.set_kind(Event::NETWORK_SPEED);
  auto speed = tx_event.mutable_network_speed();
  speed->set_throughput(data.speed_data().sent());
  buffer()->Add(tx_event);

  Event rx_event;
  rx_event.set_pid(pid);
  rx_event.set_group_id(Event::NETWORK_RX);
  rx_event.set_kind(Event::NETWORK_SPEED);
  speed = rx_event.mutable_network_speed();
  speed->set_throughput(data.speed_data().received());
  buffer()->Add(rx_event);
}

}  // namespace profiler
