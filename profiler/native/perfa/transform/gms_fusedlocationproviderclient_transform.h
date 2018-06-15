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
#ifndef GMS_FUSEDLOCATIONPROVIDERCLIENT_TRANSFORM_H
#define GMS_FUSEDLOCATIONPROVIDERCLIENT_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class GmsFusedLocationProviderClientTransform : public Transform {
 public:
  GmsFusedLocationProviderClientTransform()
      : Transform(
            "Lcom/google/android/gms/location/FusedLocationProviderClient;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    slicer::MethodInstrumenter mi_req(dex_ir);
    mi_req.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/gms/"
                     "FusedLocationProviderClientWrapper;",
                     "wrapRequestLocationUpdates"),
        true);
    if (!mi_req.InstrumentMethod(ir::MethodId(
            GetClassName(), "requestLocationUpdates",
            "(Lcom/google/android/gms/location/LocationRequest;"
            "Lcom/google/android/gms/location/LocationCallback;"
            "Landroid/os/Looper;)Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.requestLocationUpdates("
          "LocationCallback)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            GetClassName(), "requestLocationUpdates",
            "(Lcom/google/android/gms/location/LocationRequest;Landroid/app/"
            "PendingIntent;)Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.requestLocationUpdates(PendingIntent)");
    }

    slicer::MethodInstrumenter mi_rmv(dex_ir);
    mi_rmv.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/gms/"
                     "FusedLocationProviderClientWrapper;",
                     "wrapRemoveLocationUpdates"),
        true);
    if (!mi_rmv.InstrumentMethod(
            ir::MethodId(GetClassName(), "removeLocationUpdates",
                         "(Lcom/google/android/gms/location/LocationCallback;)"
                         "Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.removeLocationUpdates("
          "LocationCallback)");
    }
    if (!mi_rmv.InstrumentMethod(
            ir::MethodId(GetClassName(), "removeLocationUpdates",
                         "(Landroid/app/PendingIntent;)"
                         "Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.removeLocationUpdates(PendingIntent)");
    }
  }
};

}  // namespace profiler

#endif  // GMS_FUSEDLOCATIONPROVIDERCLIENT_TRANSFORM_H
