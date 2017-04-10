/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "instrumentation.h"
#include "dex_ir_builder.h"

namespace slicer {

bool EntryHook::Apply(lir::CodeIr* code_ir) {
  ir::Builder builder(code_ir->dex_ir);
  const auto ir_method = code_ir->ir_method;

  // construct the hook method declaration
  std::vector<ir::Type*> param_types;
  if ((ir_method->access_flags & dex::kAccStatic) == 0) {
    param_types.push_back(ir_method->parent_class->type);
  }
  if (ir_method->decl->prototype->param_types != nullptr) {
    const auto& orig_param_types = ir_method->decl->prototype->param_types->types;
    param_types.insert(param_types.end(), orig_param_types.begin(), orig_param_types.end());
  }

  auto ir_proto = builder.GetProto(builder.GetType("V"),
                                   builder.GetTypeList(param_types));

  auto ir_method_decl = builder.GetMethodDecl(
      builder.GetAsciiString(hook_method_id_.method_name), ir_proto,
      builder.GetType(hook_method_id_.class_descriptor));

  auto hook_method = code_ir->Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

  // argument registers
  auto regs = ir_method->code->registers;
  auto args_count = ir_method->code->ins_count;
  auto args = code_ir->Alloc<lir::VRegRange>(regs - args_count, args_count);

  // invoke hook bytecode
  auto hook_invoke = code_ir->Alloc<lir::Bytecode>();
  hook_invoke->opcode = dex::OP_INVOKE_STATIC_RANGE;
  hook_invoke->operands.push_back(args);
  hook_invoke->operands.push_back(hook_method);

  // insert the hook before the first bytecode in the method body
  for (auto instr : code_ir->instructions) {
    auto bytecode = dynamic_cast<lir::Bytecode*>(instr);
    if (bytecode == nullptr) {
      continue;
    }
    code_ir->instructions.InsertBefore(bytecode, hook_invoke);
    break;
  }

  return true;
}

bool ExitHook::Apply(lir::CodeIr* code_ir) {
  ir::Builder builder(code_ir->dex_ir);
  const auto ir_method = code_ir->ir_method;
  const auto return_type = ir_method->decl->prototype->return_type;

  // do we have a void-return method?
  bool return_void = (::strcmp(return_type->descriptor->c_str(), "V") == 0);

  // construct the hook method declaration
  std::vector<ir::Type*> param_types;
  if (!return_void) {
    param_types.push_back(return_type);
  }

  auto ir_proto = builder.GetProto(return_type, builder.GetTypeList(param_types));

  auto ir_method_decl = builder.GetMethodDecl(
      builder.GetAsciiString(hook_method_id_.method_name), ir_proto,
      builder.GetType(hook_method_id_.class_descriptor));

  auto hook_method = code_ir->Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

  // find and instrument all return instructions
  for (auto instr : code_ir->instructions) {
    auto bytecode = dynamic_cast<lir::Bytecode*>(instr);
    if (bytecode == nullptr) {
      continue;
    }

    dex::Opcode move_result_opcode = dex::OP_NOP;
    dex::u4 reg = 0;
    int reg_count = 0;

    switch (bytecode->opcode) {
      case dex::OP_RETURN_VOID:
        CHECK(return_void);
        break;
      case dex::OP_RETURN:
        CHECK(!return_void);
        move_result_opcode = dex::OP_MOVE_RESULT;
        reg = bytecode->CastOperand<lir::VReg>(0)->reg;
        reg_count = 1;
        break;
      case dex::OP_RETURN_OBJECT:
        CHECK(!return_void);
        move_result_opcode = dex::OP_MOVE_RESULT_OBJECT;
        reg = bytecode->CastOperand<lir::VReg>(0)->reg;
        reg_count = 1;
        break;
      case dex::OP_RETURN_WIDE:
        CHECK(!return_void);
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

    // move result back to the right register
    //
    // NOTE: we're reusing the original return's operand,
    //   which is valid and more efficient than allocating
    //   a new LIR node, but it's also fragile: we need to be
    //   very careful about mutating shared nodes.
    //
    if (move_result_opcode != dex::OP_NOP) {
      auto move_result = code_ir->Alloc<lir::Bytecode>();
      move_result->opcode = move_result_opcode;
      move_result->operands.push_back(bytecode->operands[0]);
      code_ir->instructions.InsertBefore(bytecode, move_result);
    }
  }

  return true;
}

bool DetourVirtualInvoke::Apply(lir::CodeIr* code_ir) {
  ir::Builder builder(code_ir->dex_ir);

  // search for matching invoke-virtual[/range] bytecodes
  for (auto instr : code_ir->instructions) {
    auto bytecode = dynamic_cast<lir::Bytecode*>(instr);
    if (bytecode == nullptr) {
      continue;
    }

    dex::Opcode new_call_opcode = dex::OP_NOP;
    switch (bytecode->opcode) {
      case dex::OP_INVOKE_VIRTUAL:
        new_call_opcode = dex::OP_INVOKE_STATIC;
        break;
      case dex::OP_INVOKE_VIRTUAL_RANGE:
        new_call_opcode = dex::OP_INVOKE_STATIC_RANGE;
        break;
      default:
        // skip instruction ...
        continue;
    }
    assert(new_call_opcode != dex::OP_NOP);

    auto orig_method = bytecode->CastOperand<lir::Method>(1)->ir_method;
    if (!orig_method_id_.Match(orig_method)) {
      // this is not the method you're looking for...
      continue;
    }

    // construct the detour method declaration
    // (matching the original method, plus an explicit "this" argument)
    std::vector<ir::Type*> param_types;
    param_types.push_back(orig_method->parent);
    if (orig_method->prototype->param_types != nullptr) {
      const auto& orig_param_types = orig_method->prototype->param_types->types;
      param_types.insert(param_types.end(), orig_param_types.begin(), orig_param_types.end());
    }

    auto ir_proto = builder.GetProto(orig_method->prototype->return_type,
                                     builder.GetTypeList(param_types));

    auto ir_method_decl = builder.GetMethodDecl(
        builder.GetAsciiString(detour_method_id_.method_name), ir_proto,
        builder.GetType(detour_method_id_.class_descriptor));

    auto detour_method = code_ir->Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

    // We mutate the original invoke bytecode in-place: this is ok
    // because lir::Instructions can't be shared (referenced multiple times)
    // in the code IR. It's also simpler and more efficient than allocating a
    // new IR invoke bytecode.
    bytecode->opcode = new_call_opcode;
    bytecode->operands[1] = detour_method;
  }

  return true;
}

bool MethodInstrumenter::InstrumentMethod(const ir::MethodId& method_id) {
  // locate the method to be instrumented
  ir::Builder builder(dex_ir_);
  auto ir_method = builder.FindMethod(method_id);
  if (ir_method == nullptr || ir_method->code == nullptr) {
    // we couldn't find the specified method, or it's an abstract method
    return false;
  }

  // apply all the queued transformations
  lir::CodeIr code_ir(ir_method, dex_ir_);
  for (const auto& transformation : transformations_) {
    if (!transformation->Apply(&code_ir)) {
      // the transformation failed, bail out...
      return false;
    }
  }
  code_ir.Assemble();
  return true;
}

}  // namespace slicer
