/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "perfd/commands/discover_profileable.h"

#include "utils/log.h"

using grpc::Status;

namespace profiler {

Status DiscoverProfileable::ExecuteOn(Daemon* daemon) {
  Log::V(Log::Tag::PROFILER, "Execute command DiscoverProfileable");
  return Status::OK;
}

}  // namespace profiler
