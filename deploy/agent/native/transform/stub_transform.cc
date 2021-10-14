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

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

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
    mi.AddTransformation<slicer::EntryHook>(
        entry_hook, slicer::EntryHook::Tweak::ArrayParams);

    // Replace the fake hook with an interpreter stub, using the return value
    // of the stub as the return value of the original method.
    mi.AddTransformation<HookToStub>(
        kFakeHook, "Lcom/android/tools/deploy/liveedit/LiveEditStubs;", "stub");

    mi.InstrumentMethod(method.get());
  }
}

std::string GetKey(ir::EncodedMethod* ir_method) {
  return ir_method->decl->parent->Decl() + "->" +
         ir_method->decl->name->c_str() +
         ir_method->decl->prototype->Signature();
}

void HookToStub::BuildCacheCheck(lir::CodeIr* code_ir,
                                 lir::Bytecode* first_instr,
                                 lir::Bytecode* last_instr) {
  ir::Builder builder(code_ir->dex_ir);

  auto original_label = code_ir->Alloc<lir::Label>(0);
  auto original_method = code_ir->Alloc<lir::CodeLocation>(original_label);
  code_ir->instructions.InsertAfter(last_instr, original_label);

  // Array packing always creates 3 scratch registers, so we can safely use
  // register 0 for this.
  auto reg = code_ir->Alloc<lir::VReg>(0);
  auto key = builder.GetAsciiString(GetKey(code_ir->ir_method).c_str());

  auto const_key = code_ir->Alloc<lir::Bytecode>();
  const_key->opcode = dex::OP_CONST_STRING;
  const_key->operands.push_back(reg);
  const_key->operands.push_back(
      code_ir->Alloc<lir::String>(key, key->orig_index));
  code_ir->instructions.InsertBefore(first_instr, const_key);

  auto return_type = builder.GetType("Z");
  auto param_types =
      builder.GetTypeList({builder.GetType("Ljava/lang/String;")});
  auto method_decl = builder.GetMethodDecl(
      builder.GetAsciiString("hasMethodBytecode"),
      builder.GetProto(return_type, param_types), builder.GetType(stub_class_));
  auto method =
      code_ir->Alloc<lir::Method>(method_decl, method_decl->orig_index);

  auto args = code_ir->Alloc<lir::VRegList>();
  args->registers.push_back(reg->reg);

  auto get_flag = code_ir->Alloc<lir::Bytecode>();
  get_flag->opcode = dex::OP_INVOKE_STATIC;
  get_flag->operands.push_back(args);
  get_flag->operands.push_back(method);
  code_ir->instructions.InsertBefore(first_instr, get_flag);

  auto mov_flag = code_ir->Alloc<lir::Bytecode>();
  mov_flag->opcode = dex::OP_MOVE_RESULT;
  mov_flag->operands.push_back(reg);
  code_ir->instructions.InsertBefore(first_instr, mov_flag);

  auto check = code_ir->Alloc<lir::Bytecode>();
  check->opcode = dex::OP_IF_EQZ;
  check->operands.push_back(reg);
  check->operands.push_back(original_method);
  code_ir->instructions.InsertBefore(first_instr, check);
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

    if (strcmp(method->ir_method->name->c_str(), hook_name_) == 0) {
      BuildCacheCheck(code_ir, first_instr, invoke_static);
      BuildStub(code_ir, invoke_static);
      return true;
    }
  }

  return false;
}

void HookToStub::BuildStub(lir::CodeIr* code_ir, lir::Bytecode* invoke_static) {
  ir::Builder builder(code_ir->dex_ir);

  const auto declared_return_type =
      code_ir->ir_method->decl->prototype->return_type;

  // The stub has the same return type as the instrumented method, with one
  // exception: all methods that return reference types are stubbed with an
  // interpeter call that returns an Object.
  auto return_type = declared_return_type;
  if (return_type->GetCategory() == ir::Type::Category::Reference) {
    return_type = builder.GetType("Ljava/lang/Object;");
  }

  // The interpreter stub accepts the method name and the original method's
  // parameters packaged into an Object array.
  auto param_types =
      builder.GetTypeList({builder.GetType("Ljava/lang/Class;"),
                           builder.GetType("[Ljava/lang/Object;")});

  std::string stub_method_name = stub_prefix_;
  stub_method_name += return_type->descriptor->c_str()[0];

  auto stub_method_decl = builder.GetMethodDecl(
      builder.GetAsciiString(stub_method_name.c_str()),
      builder.GetProto(return_type, param_types), builder.GetType(stub_class_));

  auto stub_method = code_ir->Alloc<lir::Method>(stub_method_decl,
                                                 stub_method_decl->orig_index);

  auto class_type = code_ir->ir_method->decl->parent;
  auto const_class = code_ir->Alloc<lir::Bytecode>();
  const_class->opcode = dex::OP_CONST_CLASS;
  const_class->operands.push_back(code_ir->Alloc<lir::VReg>(0));
  const_class->operands.push_back(
      code_ir->Alloc<lir::Type>(class_type, class_type->orig_index));

  code_ir->instructions.InsertBefore(invoke_static, const_class);

  // Modify the fake entry hook invoke to call the correct interpreter stub.
  invoke_static->operands[0] = code_ir->Alloc<lir::VRegRange>(0, 2);
  invoke_static->operands[1] = stub_method;

  dex::Opcode move_op;
  dex::Opcode ret_op;
  lir::Operand* ret_reg;
  switch (return_type->GetCategory()) {
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
      auto return_void = code_ir->Alloc<lir::Bytecode>();
      return_void->opcode = dex::OP_RETURN_VOID;
      code_ir->instructions.InsertAfter(invoke_static, return_void);
      return;
  }

  auto move_result = code_ir->Alloc<lir::Bytecode>();
  move_result->opcode = move_op;
  move_result->operands.push_back(ret_reg);

  auto ret_result = code_ir->Alloc<lir::Bytecode>();
  ret_result->opcode = ret_op;
  ret_result->operands.push_back(ret_reg);

  if (return_type->GetCategory() == ir::Type::Category::Reference) {
    // Reference types need to be cast from Object to their original type;
    // otherwise, verification will fail.
    auto cast_result = code_ir->Alloc<lir::Bytecode>();
    cast_result->opcode = dex::OP_CHECK_CAST;
    cast_result->operands.push_back(ret_reg);
    cast_result->operands.push_back(code_ir->Alloc<lir::Type>(
        declared_return_type, declared_return_type->orig_index));

    code_ir->instructions.InsertAfter(invoke_static, move_result);
    code_ir->instructions.InsertAfter(move_result, cast_result);
    code_ir->instructions.InsertAfter(cast_result, ret_result);
  } else {
    code_ir->instructions.InsertAfter(invoke_static, move_result);
    code_ir->instructions.InsertAfter(move_result, ret_result);
  }
}

}  // namespace deploy
