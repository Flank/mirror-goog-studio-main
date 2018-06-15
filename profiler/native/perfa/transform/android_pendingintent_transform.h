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
#ifndef ANDROID_PENDINGINTENT_TRANSFORM_H
#define ANDROID_PENDINGINTENT_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidPendingIntentTransform : public Transform {
 public:
  AndroidPendingIntentTransform() : Transform("Landroid/app/PendingIntent;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    slicer::MethodInstrumenter mi_activity(dex_ir);
    mi_activity.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetActivityEntry"));
    mi_activity.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetActivityExit"));
    if (!mi_activity.InstrumentMethod(
            ir::MethodId(GetClassName(), "getActivity",
                         "(Landroid/content/Context;ILandroid/content/Intent;I"
                         "Landroid/os/Bundle;)Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getActivity");
    }

    slicer::MethodInstrumenter mi_service(dex_ir);
    mi_service.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetServiceEntry"));
    mi_service.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetServiceExit"));
    if (!mi_service.InstrumentMethod(
            ir::MethodId(GetClassName(), "getService",
                         "(Landroid/content/Context;ILandroid/content/Intent;I)"
                         "Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getService");
    }

    slicer::MethodInstrumenter mi_broadcast(dex_ir);
    mi_broadcast.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetBroadcastEntry"));
    mi_broadcast.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetBroadcastExit"));
    if (!mi_broadcast.InstrumentMethod(
            ir::MethodId(GetClassName(), "getBroadcast",
                         "(Landroid/content/Context;ILandroid/content/Intent;I)"
                         "Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getBroadcast");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_PENDINGINTENT_TRANSFORM_H
