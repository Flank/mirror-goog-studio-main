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
#ifndef ANDROIDX_FRAGMENT_TRANSFORM_H
#define ANDROIDX_FRAGMENT_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidXFragmentTransform : public Transform {
 public:
  AndroidXFragmentTransform() : Transform("Landroidx/fragment/app/Fragment;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId(
            "Lcom/android/tools/profiler/support/event/FragmentWrapper;",
            "wrapOnResume"),
        true);
    if (!mi.InstrumentMethod(
            ir::MethodId(GetClassName(), "performResume", "()V"))) {
      Log::E(
          "Error instrumenting androidx.fragment.app.Fragment.performResume");
    }

    slicer::MethodInstrumenter mi_stop(dex_ir);
    mi_stop.AddTransformation<slicer::EntryHook>(
        ir::MethodId(
            "Lcom/android/tools/profiler/support/event/FragmentWrapper;",
            "wrapOnPause"),
        true);
    if (!mi_stop.InstrumentMethod(
            ir::MethodId(GetClassName(), "performPause", "()V"))) {
      Log::E("Error instrumenting androidx.fragment.app.Fragment.performPause");
    }
  }
};

}  // namespace profiler

#endif  // ANDROIDX_FRAGMENT_TRANSFORM_H
