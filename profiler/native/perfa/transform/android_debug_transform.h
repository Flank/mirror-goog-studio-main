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
#ifndef ANDROID_DEBUG_TRANSFORM_H
#define ANDROID_DEBUG_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidDebugTransform : public Transform {
 public:
  AndroidDebugTransform() : Transform("Landroid/os/Debug;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    // Instrument startMethodTracing(String tracePath) at entry.
    slicer::MethodInstrumenter mi_start(dex_ir);
    mi_start.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onStartMethodTracing"));
    if (!mi_start.InstrumentMethod(ir::MethodId(
            GetClassName(), "startMethodTracing", "(Ljava/lang/String;)V"))) {
      Log::E("Error instrumenting Debug.startMethodTracing(String)");
    }

    // Instrument stopMethodTracing() at exit.
    slicer::MethodInstrumenter mi_stop(dex_ir);
    mi_stop.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onStopMethodTracing"));
    if (!mi_stop.InstrumentMethod(
            ir::MethodId(GetClassName(), "stopMethodTracing", "()V"))) {
      Log::E("Error instrumenting Debug.stopMethodTracing");
    }

    // Instrument fixTracePath() at entry.
    slicer::MethodInstrumenter mi_fix_entry(dex_ir);
    mi_fix_entry.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onFixTracePathEntry"));
    if (!mi_fix_entry.InstrumentMethod(
            ir::MethodId(GetClassName(), "fixTracePath",
                         "(Ljava/lang/String;)Ljava/lang/String;"))) {
      Log::E("Error instrumenting Debug.fixTracePath entry");
    }

    // Instrument fixTracePath() at exit.
    slicer::MethodInstrumenter mi_fix_exit(dex_ir);
    mi_fix_exit.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onFixTracePathExit"));
    if (!mi_fix_exit.InstrumentMethod(
            ir::MethodId(GetClassName(), "fixTracePath",
                         "(Ljava/lang/String;)Ljava/lang/String;"))) {
      Log::E("Error instrumenting Debug.fixTracePath exit");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_DEBUG_TRANSFORM_H
