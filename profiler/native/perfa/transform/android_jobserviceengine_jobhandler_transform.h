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
#ifndef ANDROID_JOBSERVICEENGINE_JOBHANDLER_TRANSFORM_H
#define ANDROID_JOBSERVICEENGINE_JOBHANDLER_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidJobServiceEngineJobHandlerTransform : public Transform {
 public:
  AndroidJobServiceEngineJobHandlerTransform()
      : Transform("Landroid/app/job/JobServiceEngine$JobHandler;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    // ackStartMessage is non-abstract and calls onStartJob.
    slicer::MethodInstrumenter mi_start(dex_ir);
    mi_start.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapOnStartJob"),
        true);
    if (!mi_start.InstrumentMethod(
            ir::MethodId(GetClassName(), "ackStartMessage",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobHandler.ackStartMessage");
    }

    // ackStopMessage is non-abstract and calls onStopJob.
    slicer::MethodInstrumenter mi_stop(dex_ir);
    mi_stop.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapOnStopJob"),
        true);
    if (!mi_stop.InstrumentMethod(
            ir::MethodId(GetClassName(), "ackStopMessage",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobHandler.ackStopMessage");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_JOBSERVICEENGINE_JOBHANDLER_TRANSFORM_H
