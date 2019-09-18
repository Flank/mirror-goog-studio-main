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

#ifdef APP_INSPECTION_EXPERIMENT
#ifndef VOID_EXIT_HOOK_H
#define VOID_EXIT_HOOK_H

#include <string>
#include "slicer/instrumentation.h"

using namespace slicer;

namespace app_inspection {

// Insert a call to the "exit hook" method before every return
// in the instrumented method. The "exit hook" will be passed the
// original return value as an object.
// In comparison to slicer:ExitHook it doesn't override result, injected method
// has simply "void" return type and receives returned object as "Object" type
// instead of actual type.
class VoidExitHook : public Transformation {
 public:
  explicit VoidExitHook(const ir::MethodId& hook_method_id)
      : hook_method_id_(hook_method_id) {
    // hook method signature is generated automatically
    SLICER_CHECK(hook_method_id_.signature == nullptr);
  }

  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  ir::MethodId hook_method_id_;
};

}  // namespace app_inspection

#endif
#endif  // APP_INSPECTION_EXPERIMENT