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
#include "network_connection_count_sampler.h"

#include "perfd/event_buffer.h"

#include "proto/common.pb.h"
#include "proto/network.pb.h"

namespace profiler {

using proto::Event;

void NetworkConnectionCountSampler::Sample() {
  sampler_.Refresh();
  auto data = sampler_.Sample(uid_);

  Event event;
  event.set_session_id(session().info().session_id());
  event.set_kind(Event::NETWORK_CONNECTION_COUNT);
  auto speed = event.mutable_network_connections();
  speed->set_num_connections(data.connection_data().connection_number());
  buffer()->Add(event);
}

}  // namespace profiler
