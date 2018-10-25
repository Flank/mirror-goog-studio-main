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

#include "tools/base/deploy/common/utils.h"

#include <sys/types.h>
#include <unistd.h>

#include "tools/base/deploy/common/log.h"

namespace deploy {

deploy::Event ConvertProtoEventToEvent(
    const proto::Event& proto_event) noexcept {
  Event event;
  event.tid = proto_event.tid();
  event.pid = proto_event.pid();
  event.text = proto_event.text();
  event.timestamp_ns = proto_event.timestamp_ns();
  deploy::Event::Type type;
  switch (proto_event.type()) {
    case proto::Event::LOG_ERR:
      type = Event::Type::Error;
      break;
    case proto::Event::LOG_OUT:
      type = Event::Type::Logging;
      break;
    case proto::Event::TRC_BEG:
      type = Event::Type::Begin;
      break;
    case proto::Event::TRC_END:
      type = Event::Type::End;
      break;
  }
  event.type = type;
  return event;
}

void ConvertEventToProtoEvent(deploy::Event& event,
                              proto::Event* proto_event) noexcept {
  proto::Event_Type proto_type;
  switch (event.type) {
    case deploy::Event::Type::Begin:
      proto_type = proto::Event_Type_TRC_BEG;
      break;
    case deploy::Event::Type::End:
      proto_type = proto::Event_Type_TRC_END;
      break;
    case deploy::Event::Type::Error:
      proto_type = proto::Event_Type::Event_Type_LOG_ERR;
      break;
    case deploy::Event::Type::Logging:
      proto_type = proto::Event_Type::Event_Type_LOG_OUT;
      break;
  }
  proto_event->set_type(proto_type);
  proto_event->set_text(event.text);
  proto_event->set_pid(event.pid);
  proto_event->set_tid(event.tid);
  proto_event->set_timestamp_ns(event.timestamp_ns);
}
}  // namespace deploy
