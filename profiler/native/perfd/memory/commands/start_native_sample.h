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
#ifndef PERFD_MEMORY_COMMANDS_START_NATIVE_SAMPLE_H
#define PERFD_MEMORY_COMMANDS_START_NATIVE_SAMPLE_H

#include "daemon/daemon.h"
#include "perfd/memory/native_heap_manager.h"
#include "perfd/sessions/sessions_manager.h"
#include "proto/commands.pb.h"

namespace profiler {

class StartNativeSample : public CommandT<StartNativeSample> {
 public:
  StartNativeSample(const proto::Command& command,
                    NativeHeapManager* heap_sampler,
                    SessionsManager* sessions_manager)
      : CommandT(command),
        heap_sampler_(heap_sampler),
        sessions_manager_(sessions_manager) {}

  static Command* Create(const proto::Command& command,
                         NativeHeapManager* heap_sampler,
                         SessionsManager* sessions_manager) {
    return new StartNativeSample(command, heap_sampler, sessions_manager);
  }

  // Starts recording a heapprofd sample. This generates a single event.
  // |MEMORY_NATIVE_SAMPLE_STATUS| event indicating if the recording has started
  // or an error was generated. If an error occurs the |failure_message|  field
  // is populated.
  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  NativeHeapManager* heap_sampler_;
  SessionsManager* sessions_manager_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_COMMANDS_START_NATIVE_SAMPLE_H
