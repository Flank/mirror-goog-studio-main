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
#ifndef ANDROID_JOBSERVICE_TRANSFORM_H
#define ANDROID_JOBSERVICE_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidJobServiceTransform : public Transform {
 public:
  AndroidJobServiceTransform() : Transform("Landroid/app/job/JobService;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapJobFinished"));
    if (!mi.InstrumentMethod(
            ir::MethodId(GetClassName(), "jobFinished",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobService.jobFinished");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_JOBSERVICE_TRANSFORM_H
