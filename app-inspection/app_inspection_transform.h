/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifdef APP_INSPECTION_EXPERIMENT
#ifndef APP_INSPECTION_TRANSFORM_H
#define APP_INSPECTION_TRANSFORM_H

#include "slicer/dex_ir.h"
#include "slicer/dex_ir_builder.h"
#include "slicer/instrumentation.h"
#include "utils/log.h"
#include "void_exit_hook.h"

namespace app_inspection {

class AppInspectionTransform {
 public:
  AppInspectionTransform(const char* class_name) : class_name_(class_name){};

  void AddTransform(const char* class_name, const char* method_name,
                    const char* signature, bool isEntry) {
    transforms.push_back(
        TransformDescription(class_name, method_name, signature, isEntry));
  }

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) {
    for (auto transform : transforms) {
      slicer::MethodInstrumenter mi(dex_ir);
      if (transform.isEntry()) {
        mi.AddTransformation<slicer::EntryHook>(
            ir::MethodId("Lcom/android/tools/agent/app/inspection/"
                         "AppInspectionService$ExperimentalCapabilities;",
                         "onEntry"),
            slicer::EntryHook::Tweak::ThisAsObject);
      } else {
        mi.AddTransformation<VoidExitHook>(
            ir::MethodId("Lcom/android/tools/agent/app/inspection/"
                         "AppInspectionService$ExperimentalCapabilities;",
                         "onExit"));
      }

      if (!mi.InstrumentMethod(ir::MethodId(transform.GetClassName(),
                                            transform.GetMethod(),
                                            transform.GetSignature()))) {
        profiler::Log::E(profiler::Log::Tag::APPINSPECT,
                         "Error enter instrumenting %s\n", GetClassName());
      }
    }
  }

  const char* GetClassName() { return class_name_.c_str(); }

 private:
  class TransformDescription {
   public:
    TransformDescription(const char* class_name, const char* method_name,
                         const char* signature, bool isEntry)
        : class_name_(class_name),
          method_name_(method_name),
          signature_(signature),
          isEntry_(isEntry) {}

    const char* GetClassName() { return class_name_.c_str(); }

    const char* GetMethod() { return method_name_.c_str(); }

    const char* GetSignature() { return signature_.c_str(); }

    bool isEntry() { return isEntry_; }

   private:
    std::string class_name_;
    std::string method_name_;
    std::string signature_;
    bool isEntry_;
  };

  std::string class_name_;
  std::list<TransformDescription> transforms;
};

}  // namespace app_inspection

#endif  // APP_INSPECTION_TRANSFORM_H
#endif  // APP_INSPECTION_EXPERIMENT
