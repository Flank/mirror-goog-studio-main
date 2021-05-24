/*
 * Copyright (C) 2021 The Android Open Source Project
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
 *
 */

#ifndef HOOK_TRANSFORM_H
#define HOOK_TRANSFORM_H

#include <jvmti.h>

#include <memory>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"

namespace deploy {

struct MethodHooks {
  const std::string method_name;
  const std::string method_signature;
  const std::string entry_hook;
  const std::string exit_hook;

  static const std::string kNoHook;

  MethodHooks(const std::string& method_name,
              const std::string& method_signature,
              const std::string& entry_hook, const std::string& exit_hook)
      : method_name(method_name),
        method_signature(method_signature),
        entry_hook(entry_hook),
        exit_hook(exit_hook) {}
};

class HookTransform : public Transform {
 public:
  HookTransform(const std::string& class_name, const std::string& method_name,
                const std::string& method_signature,
                const std::string& entry_hook, const std::string& exit_hook)
      : Transform(class_name) {
    hooks_.emplace_back(method_name, method_signature, entry_hook, exit_hook);
  }

  HookTransform(const std::string& class_name,
                const std::vector<MethodHooks>& hooks)
      : Transform(class_name), hooks_(hooks) {}

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) const override;

 private:
  const char* kHookClassName =
      "Lcom/android/tools/deploy/instrument/InstrumentationHooks;";
  std::vector<MethodHooks> hooks_;
};

}  // namespace deploy

#endif