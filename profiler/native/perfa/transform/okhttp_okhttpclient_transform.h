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
#ifndef OKHTTP_OKHTTPCLIENT_TRANFORM_H
#define OKHTTP_OKHTTPCLIENT_TRANFORM_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "transform.h"
#include "utils/log.h"

namespace profiler {

class OkhttpClientTransform : public Transform {
 public:
  OkhttpClientTransform() : Transform("Lcom/squareup/okhttp/OkHttpClient;") {}

  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) override {
    slicer::MethodInstrumenter mi(dex_ir);
    // Add Entry hook method with this argument passed as type Object.
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/network/okhttp/"
                     "OkHttp2Wrapper;",
                     "setOkHttpClassLoader"),
        true);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/okhttp/OkHttp2Wrapper;",
        "insertInterceptor"));
    if (!mi.InstrumentMethod(ir::MethodId(GetClassName(), "networkInterceptors",
                                          "()Ljava/util/List;"))) {
      Log::E("Error instrumenting OkHttp2 OkHttpClient");
    }
  }
};

}  // namespace profiler

#endif  // OKHTTP_OKHTTPCLIENT_TRANFORM_H
