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
 */
#ifndef ARRAY_PARAMS_ENTRY_HOOK_H
#define ARRAY_PARAMS_ENTRY_HOOK_H

#include "slicer/dex_ir.h"
#include "slicer/dex_ir_builder.h"
#include "slicer/instrumentation.h"
#include "utils/log.h"

namespace app_inspection {

class ArrayParamsEntryHook : public slicer::Transformation {
 public:
  explicit ArrayParamsEntryHook(const ir::MethodId& hook_method_id)
      : hook_method_id_(hook_method_id) {
    // hook method signature is generated automatically
    SLICER_CHECK(hook_method_id_.signature == nullptr);
  }

  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  ir::MethodId hook_method_id_;

  bool InjectArrayParamsHook(lir::CodeIr* code_ir, lir::Bytecode* bytecode);
};

}  // namespace app_inspection

#endif  // ARRAY_PARAMS_ENTRY_HOOK_H
