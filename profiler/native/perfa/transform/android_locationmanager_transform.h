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
#ifndef ANDROID_LOCATIONMANAGER_TRANSFORM_H
#define ANDROID_LOCATIONMANAGER_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class AndroidLocationManagerTransform : public Transform {
 public:
  AndroidLocationManagerTransform()
      : Transform("Landroid/location/LocationManager;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    // Instrument all versions of requestLocationUpdates.
    slicer::MethodInstrumenter mi_req(dex_ir);
    mi_req.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRequestLocationUpdates"));
    if (!mi_req.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestLocationUpdates",
                         "(Ljava/lang/String;JFLandroid/location/"
                         "LocationListener;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, LocationListener)");
    }
    if (!mi_req.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestLocationUpdates",
                         "(JFLandroid/location/Criteria;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(long, "
          "float, Criteria, LocationListener, Looper)");
    }
    if (!mi_req.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestLocationUpdates",
                         "(Ljava/lang/String;JFLandroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, LocationListener, Looper)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            GetClassName(), "requestLocationUpdates",
            "(JFLandroid/location/Criteria;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(long, "
          "float, Criteria, PendingIntent)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            GetClassName(), "requestLocationUpdates",
            "(Ljava/lang/String;JFLandroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, PendingIntent)");
    }

    // Instrument all versions of requestSingleUpdate.
    slicer::MethodInstrumenter mi_req_s(dex_ir);
    mi_req_s.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRequestSingleUpdate"));
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestSingleUpdate",
                         "(Ljava/lang/String;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(String, "
          "PendingIntent)");
    }
    if (!mi_req_s.InstrumentMethod(ir::MethodId(
            GetClassName(), "requestSingleUpdate",
            "(Landroid/location/Criteria;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(Criteria, "
          "PendingIntent)");
    }
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestSingleUpdate",
                         "(Ljava/lang/String;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(String, "
          "LocationListener, Looper)");
    }
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(GetClassName(), "requestSingleUpdate",
                         "(Landroid/location/Criteria;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(Criteria, "
          "LocationListener, Looper)");
    }

    // Instrument all versions of removeUpdates
    slicer::MethodInstrumenter mi_remove(dex_ir);
    mi_remove.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRemoveUpdates"));
    if (!mi_remove.InstrumentMethod(
            ir::MethodId(GetClassName(), "removeUpdates",
                         "(Landroid/location/LocationListener;)V"))) {
      Log::E(
          "Error instrumenting "
          "LocationManager.removeUpdates(LocationListener)");
    }
    if (!mi_remove.InstrumentMethod(
            ir::MethodId(GetClassName(), "removeUpdates",
                         "(Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.removeUpdates(PendingIntent)");
    }
  }
};

}  // namespace profiler

#endif  // ANDROID_LOCATIONMANAGER_TRANSFORM_H
