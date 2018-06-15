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
#ifndef ANDROID_ALARMMANAGER_TRANSFORM_H
#define ANDROID_ALARMMANAGER_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidAlarmManagerTransform : public Transform {
 public:
  AndroidAlarmManagerTransform() : Transform("Landroid/app/AlarmManager;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    // Instrument setImpl.
    slicer::MethodInstrumenter mi_set(dex_ir);
    mi_set.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/AlarmManagerWrapper;",
        "wrapSetImpl"));
    if (!mi_set.InstrumentMethod(ir::MethodId(
            GetClassName(), "setImpl",
            "(IJJJILandroid/app/PendingIntent;"
            "Landroid/app/AlarmManager$OnAlarmListener;Ljava/lang/String;"
            "Landroid/os/Handler;Landroid/os/WorkSource;"
            "Landroid/app/AlarmManager$AlarmClockInfo;)V"))) {
      Log::E("Error instrumenting AlarmManager.setImpl");
    }

    // Instrument cancel(PendingIntent) and cancel(OnAlarmListener).
    slicer::MethodInstrumenter mi_cancel(dex_ir);
    mi_cancel.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/AlarmManagerWrapper;",
        "wrapCancel"));
    if (!mi_cancel.InstrumentMethod(ir::MethodId(
            GetClassName(), "cancel", "(Landroid/app/PendingIntent;)V"))) {
      Log::E("Error instrumenting AlarmManager.cancel(PendingIntent)");
    }
    if (!mi_cancel.InstrumentMethod(
            ir::MethodId(GetClassName(), "cancel",
                         "(Landroid/app/AlarmManager$OnAlarmListener;)V"))) {
      Log::E("Error instrumenting AlarmManager.cancel(OnAlarmListener)");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_ALARMMANAGER_TRANSFORM_H
