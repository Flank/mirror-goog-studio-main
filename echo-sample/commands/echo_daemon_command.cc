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
#include "echo_daemon_command.h"

#include <string>
#include "proto/common.pb.h"
#include "utils/process_manager.h"

using grpc::Status;
using grpc::StatusCode;
using profiler::proto::Event;
using std::string;

namespace demo {

Status EchoDaemonCommand::ExecuteOn(profiler::Daemon* daemon) {
  profiler::proto::Event event;
  event.set_kind(profiler::proto::Event::ECHO);
  // Setting is ended to true so the query does not return the +1/-1 result when
  // querying a specific time range.
  event.set_is_ended(true);
  auto* echo = event.mutable_echo();
  echo->set_data(std::string("<from Daemon> ").append(data_.data()));
  daemon->buffer()->Add(event);
  return Status::OK;
}

}  // namespace demo
