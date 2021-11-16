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

#include "tools/base/deploy/agent/native/transform/stub_transform.h"

#include <algorithm>

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {
namespace {

const char* kFakeHook = "APPLYCHANGES";
const char* kFakeHookClass = "LApply/Changes;";

const char* kStubPrefix = "stub";
const char* kStubClass = "Lcom/android/tools/deploy/liveedit/LiveEditStubs;";

lir::Bytecode* GetInstr(lir::CodeIr* code_ir, dex::Opcode opcode,
                        std::vector<lir::Operand*> operands) {
  auto instr = code_ir->Alloc<lir::Bytecode>();
  instr->opcode = opcode;
  instr->operands = operands;
  return instr;
}

lir::String* GetString(ir::Builder* builder, lir::CodeIr* code_ir,
                       const std::string& string) {
  auto ir_string = builder->GetAsciiString(string.c_str());
  return code_ir->Alloc<lir::String>(ir_string, ir_string->orig_index);
}

lir::Method* GetMethod(ir::Builder* builder, lir::CodeIr* code_ir,
                       const std::string& parent_type,
                       const std::string& method_name,
                       const std::string& return_type,
                       const std::vector<std::string> param_types) {
  auto ir_parent_type = builder->GetType(parent_type.c_str());
  auto ir_return_type = builder->GetType(return_type.c_str());
  std::vector<ir::Type*> ir_types;
  for (auto type : param_types) {
    ir_types.push_back(builder->GetType(type.c_str()));
  }
  auto ir_param_types = builder->GetTypeList(ir_types);
  auto method_decl = builder->GetMethodDecl(
      builder->GetAsciiString(method_name.c_str()),
      builder->GetProto(ir_return_type, ir_param_types), ir_parent_type);
  return code_ir->Alloc<lir::Method>(method_decl, method_decl->orig_index);
}
}  // namespace

void StubTransform::Apply(std::shared_ptr<ir::DexFile> dex_ir) const {
  for (auto& method : dex_ir->encoded_methods) {
    // Skip the constructor and static blocks; our instrumentation doesn't
    // work in those contexts.
    if (strcmp(method->decl->name->c_str(), "<init>") == 0 ||
        strcmp(method->decl->name->c_str(), "<clinit>") == 0) {
      continue;
    }
    slicer::MethodInstrumenter mi(dex_ir);

    // Transform the IR with a fake entry hook. This adds a static invocation
    // before the method body excutes, and handles packing the method
    // parameters into an array.
    const ir::MethodId entry_hook(kFakeHookClass, kFakeHook);

    // Ensure we always have 4 non-param registers to work with.
    auto non_param_regs = method->code->registers - method->code->ins_count;
    if (non_param_regs < 4) {
      mi.AddTransformation<slicer::AllocateScratchRegs>(4 - non_param_regs);
    }

    mi.AddTransformation<slicer::EntryHook>(
        entry_hook, slicer::EntryHook::Tweak::ArrayParams);

    // Replace the fake hook with an interpreter stub, using the return value
    // of the stub as the return value of the original method.
    mi.AddTransformation<HookToStub>();

    mi.InstrumentMethod(method.get());
  }
}

bool Print::Apply(lir::CodeIr* code_ir) {
  Log::V("-- %s --", code_ir->ir_method->decl->name->c_str());
  for (auto instr : code_ir->instructions) {
    BytecodeConvertingVisitor visitor;
    instr->Accept(&visitor);
    if (visitor.out != nullptr) {
      Log::V("%s", dex::GetOpcodeName(visitor.out->opcode));
    }
  }
  Log::V("-- end --");
  return true;
}

bool HookToStub::Apply(lir::CodeIr* code_ir) {
  // Iterate over the instructions list until the call to the fake hook is
  // located, then use the fake hook invocation to construct the stub.
  lir::Bytecode* first_instr = nullptr;
  for (auto instr : code_ir->instructions) {
    BytecodeConvertingVisitor visitor;
    instr->Accept(&visitor);
    if (visitor.out == nullptr) {
      continue;
    }

    if (!first_instr) {
      first_instr = visitor.out;
    }

    // The call to the hook is an INVOKE_STATIC_RANGE instruction, so we can
    // skip any instruction that doesn't have that specific opcode.
    if (visitor.out->opcode != dex::Opcode::OP_INVOKE_STATIC_RANGE) {
      continue;
    }

    auto invoke_static = visitor.out;
    auto method = invoke_static->CastOperand<lir::Method>(1);

    if (strcmp(method->ir_method->name->c_str(), kFakeHook) == 0) {
      BuildStub(code_ir, first_instr, invoke_static);
      return true;
    }
  }

  return false;
}

void HookToStub::BuildStub(lir::CodeIr* code_ir, lir::Bytecode* first_instr,
                           lir::Bytecode* invoke_static) {
  ir::Builder builder(code_ir->dex_ir);

  auto original_label = code_ir->Alloc<lir::Label>(0);
  auto original_method = code_ir->Alloc<lir::CodeLocation>(original_label);
  code_ir->instructions.InsertAfter(invoke_static, original_label);

  // We have ensured that we always have 4 available non-param registers.
  std::vector<lir::VReg*> regs = {code_ir->Alloc<lir::VReg>(0),
                                  code_ir->Alloc<lir::VReg>(1),
                                  code_ir->Alloc<lir::VReg>(2),
                                  code_ir->Alloc<lir::VReg>(3)};

  std::string internal_class_name = code_ir->ir_method->decl->parent->Decl();
  std::string method_name = code_ir->ir_method->decl->name->c_str();
  std::string method_desc = code_ir->ir_method->decl->prototype->Signature();

  std::replace(internal_class_name.begin(), internal_class_name.end(), '.',
               '/');

  auto class_name_str = GetString(&builder, code_ir, internal_class_name);
  auto method_name_str = GetString(&builder, code_ir, method_name);
  auto method_desc_str = GetString(&builder, code_ir, method_desc);

  auto const_class_name =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[0], class_name_str});
  code_ir->instructions.InsertBefore(first_instr, const_class_name);

  auto const_method_name =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[1], method_name_str});
  code_ir->instructions.InsertBefore(first_instr, const_method_name);

  auto const_method_desc =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[2], method_desc_str});
  code_ir->instructions.InsertBefore(first_instr, const_method_desc);

  auto method =
      GetMethod(&builder, code_ir, kStubClass, "shouldInterpretMethod", "Z",
                {"Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;"});

  auto args = code_ir->Alloc<lir::VRegList>();
  args->registers.push_back(0);
  args->registers.push_back(1);
  args->registers.push_back(2);

  auto get_flag = GetInstr(code_ir, dex::OP_INVOKE_STATIC, {args, method});
  code_ir->instructions.InsertBefore(first_instr, get_flag);

  auto mov_flag = GetInstr(code_ir, dex::OP_MOVE_RESULT, {regs[0]});
  code_ir->instructions.InsertBefore(first_instr, mov_flag);

  auto check = GetInstr(code_ir, dex::OP_IF_EQZ, {regs[0], original_method});
  code_ir->instructions.InsertBefore(first_instr, check);

  // Now add the stub method trampoline.

  const auto original_return_type =
      code_ir->ir_method->decl->prototype->return_type;

  // The stub has the same return type as the instrumented method, with one
  // exception: all methods that return reference types are stubbed with an
  // interpeter call that returns an Object.
  std::string new_return_type_desc = original_return_type->descriptor->c_str();
  if (original_return_type->GetCategory() == ir::Type::Category::Reference) {
    new_return_type_desc = "Ljava/lang/Object;";
  }

  std::string stub_method_name = kStubPrefix;
  stub_method_name += new_return_type_desc[0];

  // The interpreter stub accepts the parent class name, the named method
  // descriptor, and the parameters of the original method packaged into an
  // Object array.
  auto stub_method = GetMethod(
      &builder, code_ir, kStubClass, stub_method_name, new_return_type_desc,
      {"Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "[Ljava/lang/Object;"});

  // Move the parameter array to the correct position in the argument list.
  auto move_array = GetInstr(code_ir, dex::OP_MOVE_OBJECT, {regs[3], regs[1]});
  code_ir->instructions.InsertBefore(invoke_static, move_array);

  const_class_name =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[0], class_name_str});
  code_ir->instructions.InsertBefore(invoke_static, const_class_name);

  const_method_name =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[1], method_name_str});
  code_ir->instructions.InsertBefore(invoke_static, const_method_name);

  const_method_desc =
      GetInstr(code_ir, dex::OP_CONST_STRING, {regs[2], method_desc_str});
  code_ir->instructions.InsertBefore(invoke_static, const_method_desc);

  // Modify the fake entry hook invoke to call the correct interpreter stub.
  auto reg_range = code_ir->Alloc<lir::VRegRange>(0, 4);

  invoke_static->operands[0] = reg_range;
  invoke_static->operands[1] = stub_method;

  dex::Opcode move_op;
  dex::Opcode ret_op;
  lir::Operand* ret_reg;
  switch (original_return_type->GetCategory()) {
    case ir::Type::Category::Scalar:
      ret_reg = code_ir->Alloc<lir::VReg>(0);
      move_op = dex::OP_MOVE_RESULT;
      ret_op = dex::OP_RETURN;
      break;
    case ir::Type::Category::WideScalar:
      ret_reg = code_ir->Alloc<lir::VRegPair>(0);
      move_op = dex::OP_MOVE_RESULT_WIDE;
      ret_op = dex::OP_RETURN_WIDE;
      break;
    case ir::Type::Category::Reference:
      ret_reg = code_ir->Alloc<lir::VReg>(0);
      move_op = dex::OP_MOVE_RESULT_OBJECT;
      ret_op = dex::OP_RETURN_OBJECT;
      break;
    case ir::Type::Category::Void:
      // Void methods can skip the rest of the IR manipulation, as they don't
      // return anything.
      auto return_void = GetInstr(code_ir, dex::OP_RETURN_VOID, {});
      code_ir->instructions.InsertAfter(invoke_static, return_void);
      return;
  }

  auto move_result = GetInstr(code_ir, move_op, {ret_reg});
  auto ret_result = GetInstr(code_ir, ret_op, {ret_reg});

  if (original_return_type->GetCategory() == ir::Type::Category::Reference) {
    // Reference types need to be cast from Object to their original type;
    // otherwise, verification will fail.
    auto cast_type = code_ir->Alloc<lir::Type>(
        original_return_type, original_return_type->orig_index);
    auto cast_result =
        GetInstr(code_ir, dex::OP_CHECK_CAST, {ret_reg, cast_type});

    code_ir->instructions.InsertAfter(invoke_static, move_result);
    code_ir->instructions.InsertAfter(move_result, cast_result);
    code_ir->instructions.InsertAfter(cast_result, ret_result);
  } else {
    code_ir->instructions.InsertAfter(invoke_static, move_result);
    code_ir->instructions.InsertAfter(move_result, ret_result);
  }
}

}  // namespace deploy
