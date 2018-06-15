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
#ifndef ANDROID_POWERMANAGER_WAKELOCK_TRANSFORM_H
#define ANDROID_POWERMANAGER_WAKELOCK_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidPowerManagerWakeLockTransform : public Transform {
 public:
  AndroidPowerManagerWakeLockTransform()
      : Transform("Landroid/os/PowerManager$WakeLock;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    // Instrument acquire() and acquire(long).
    slicer::MethodInstrumenter mi_acq(dex_ir);
    mi_acq.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "wrapAcquire"));
    if (!mi_acq.InstrumentMethod(
            ir::MethodId(GetClassName(), "acquire", "()V"))) {
      Log::E("Error instrumenting WakeLock.acquire");
    }
    if (!mi_acq.InstrumentMethod(
            ir::MethodId(GetClassName(), "acquire", "(J)V"))) {
      Log::E("Error instrumenting WakeLock.acquire(long)");
    }

    // Instrument release(int).
    slicer::MethodInstrumenter mi_rel(dex_ir);
    mi_rel.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onReleaseEntry"));
    mi_rel.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onReleaseExit"));
    if (!mi_rel.InstrumentMethod(
            ir::MethodId(GetClassName(), "release", "(I)V"))) {
      Log::E("Error instrumenting WakeLock.release");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_POWERMANAGER_WAKELOCK_TRANSFORM_H
