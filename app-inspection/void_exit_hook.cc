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

#include "void_exit_hook.h"
#include "slicer/dex_ir.h"
#include "slicer/dex_ir_builder.h"
#include "slicer/instrumentation.h"

namespace app_inspection {

struct BytecodeConvertingVisitor : public lir::Visitor {
  lir::Bytecode* out = nullptr;
  bool Visit(lir::Bytecode* bytecode) {
    out = bytecode;
    return true;
  }
};

// copy-paste from  slicer/instrumentation.cc, but we call simply void method.
bool VoidExitHook::Apply(lir::CodeIr* code_ir) {
  ir::Builder builder(code_ir->dex_ir);
  const auto ir_method = code_ir->ir_method;
  const auto return_type = ir_method->decl->prototype->return_type;

  // do we have a void-return method?
  bool return_void = (::strcmp(return_type->descriptor->c_str(), "V") == 0);

  // construct the hook method declaration
  std::vector<ir::Type*> param_types;
  if (!return_void) {
    param_types.push_back(builder.GetType("Ljava/lang/Object;"));
  }

  auto ir_proto =
      builder.GetProto(builder.GetType("V"), builder.GetTypeList(param_types));
  auto ir_method_decl = builder.GetMethodDecl(
      builder.GetAsciiString(hook_method_id_.method_name), ir_proto,
      builder.GetType(hook_method_id_.class_descriptor));

  auto hook_method =
      code_ir->Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

  // find and instrument all return instructions
  for (auto instr : code_ir->instructions) {
    BytecodeConvertingVisitor visitor;
    instr->Accept(&visitor);
    auto bytecode = visitor.out;
    if (bytecode == nullptr) {
      continue;
    }

    dex::Opcode move_result_opcode = dex::OP_NOP;
    dex::u4 reg = 0;
    int reg_count = 0;

    switch (bytecode->opcode) {
      case dex::OP_RETURN_VOID:
        SLICER_CHECK(return_void);
        break;
      case dex::OP_RETURN:
        SLICER_CHECK(!return_void);
        move_result_opcode = dex::OP_MOVE_RESULT;
        reg = bytecode->CastOperand<lir::VReg>(0)->reg;
        reg_count = 1;
        break;
      case dex::OP_RETURN_OBJECT:
        SLICER_CHECK(!return_void);
        move_result_opcode = dex::OP_MOVE_RESULT_OBJECT;
        reg = bytecode->CastOperand<lir::VReg>(0)->reg;
        reg_count = 1;
        break;
      case dex::OP_RETURN_WIDE:
        SLICER_CHECK(!return_void);
        move_result_opcode = dex::OP_MOVE_RESULT_WIDE;
        reg = bytecode->CastOperand<lir::VRegPair>(0)->base_reg;
        reg_count = 2;
        break;
      default:
        // skip the bytecode...
        continue;
    }

    // invoke hook bytecode
    auto args = code_ir->Alloc<lir::VRegRange>(reg, reg_count);
    auto hook_invoke = code_ir->Alloc<lir::Bytecode>();
    hook_invoke->opcode = dex::OP_INVOKE_STATIC_RANGE;
    hook_invoke->operands.push_back(args);
    hook_invoke->operands.push_back(hook_method);
    code_ir->instructions.InsertBefore(bytecode, hook_invoke);
  }

  return true;
}

}  // namespace app_inspection
#endif  // APP_INSPECTION_EXPERIMENT
