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
#ifndef PERFD_MEMORY_COMMANDS_STOP_NATIVE_SAMPLE_H
#define PERFD_MEMORY_COMMANDS_STOP_NATIVE_SAMPLE_H

#include "daemon/daemon.h"
#include "perfd/memory/native_heap_manager.h"
#include "proto/commands.pb.h"

namespace profiler {

class StopNativeSample : public CommandT<StopNativeSample> {
 public:
  StopNativeSample(const proto::Command& command,
                   NativeHeapManager* heap_sampler)
      : CommandT(command), heap_sampler_(heap_sampler) {}

  static Command* Create(const proto::Command& command,
                         NativeHeapManager* heap_sampler) {
    return new StopNativeSample(command, heap_sampler);
  }

  // Stops an ongoing heapprofd recording.  This generates two events.
  // 1) |MEMORY_NATIVE_SAMPLE_STATUS| indicating that the capture has stopped
  // recording. 2) |MEMORY_NATIVE_SAMPLE_CAPTURE| with the capture id start time
  // and end time for the ui.
  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  NativeHeapManager* heap_sampler_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_COMMANDS_STOP_NATIVE_SAMPLE_H
