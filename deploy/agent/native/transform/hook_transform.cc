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

#include "tools/base/deploy/agent/native/transform/hook_transform.h"

#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

const std::string MethodHooks::kNoHook = "";

void HookTransform::Apply(std::shared_ptr<ir::DexFile> dex_ir) const {
  for (const MethodHooks& hook : hooks_) {
    slicer::MethodInstrumenter mi(dex_ir);
    if (hook.entry_hook != MethodHooks::kNoHook) {
      const ir::MethodId entry_hook(kHookClassName, hook.entry_hook.c_str());
      mi.AddTransformation<slicer::EntryHook>(
          entry_hook, slicer::EntryHook::Tweak::ThisAsObject);
    }
    if (hook.exit_hook != MethodHooks::kNoHook) {
      const ir::MethodId exit_hook(kHookClassName, hook.exit_hook.c_str());
      mi.AddTransformation<slicer::ExitHook>(exit_hook);
    }
    const std::string jni_name = "L" + GetClassName() + ";";
    const ir::MethodId target_method(jni_name.c_str(), hook.method_name.c_str(),
                                     hook.method_signature.c_str());
    if (!mi.InstrumentMethod(target_method)) {
      Log::E("Failed to instrument: %s", GetClassName().c_str());
    }
  }
}

}  // namespace deploy
