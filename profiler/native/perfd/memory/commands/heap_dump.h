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
#ifndef PERFD_MEMORY_COMMANDS_HEAP_DUMP_H
#define PERFD_MEMORY_COMMANDS_HEAP_DUMP_H

#include "daemon/daemon.h"
#include "perfd/memory/heap_dump_manager.h"
#include "proto/commands.pb.h"

namespace profiler {

class HeapDump : public CommandT<HeapDump> {
 public:
  HeapDump(const proto::Command& command, HeapDumpManager* heap_dumper)
      : CommandT(command), heap_dumper_(heap_dumper) {}

  static Command* Create(const proto::Command& command,
                         HeapDumpManager* heap_dumper) {
    return new HeapDump(command, heap_dumper);
  }

  // Request a heap dump and generates events to be added back to the Daemon's
  // event buffer. The following events are expected to be generated:
  // 1. A |MEMORY_HEAP_DUMP_STATUS| event indicating whether the heap dump could
  // be started.
  // 2. If the |MEMORY_HEAP_DUMP_STATUS| returns a |SUCCESS| status, a pair of
  // |MEMORY_HEAP_DUMP| events indicating the heap dump's start and end.
  // The start event's HeapDumpInfo is supposed to have an |end_time| of
  // LLONG_MAX and not have the |success| field set. These fields are set in the
  // end event's HeapDumpInfo message.
  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  HeapDumpManager* heap_dumper_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_COMMANDS_HEAP_DUMP_H
