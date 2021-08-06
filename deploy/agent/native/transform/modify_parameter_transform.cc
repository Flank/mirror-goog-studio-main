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

#include "tools/base/deploy/agent/native/transform/modify_parameter_transform.h"

#include <memory>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

bool ModifyParameter::Apply(lir::CodeIr* code_ir) {
  BytecodeConvertingVisitor visitor;
  for (auto instr : code_ir->instructions) {
    instr->Accept(&visitor);
    if (visitor.out != nullptr) {
      break;
    }
  }

  auto ir_method = code_ir->ir_method;
  auto regs = ir_method->code->registers;
  auto args_count = ir_method->code->ins_count;

  ir::Builder builder(code_ir->dex_ir);

  if (!ir_method->decl->prototype->param_types) {
    Log::E("Cannot modify parameter of method with no parameters");
    return false;
  }

  auto types = ir_method->decl->prototype->param_types->types;
  if (param_idx_ >= types.size()) {
    Log::E("Index %u out of range for method with parameter count %u",
           param_idx_, types.size());
    return false;
  }

  auto param_type = types[param_idx_];
  auto param_transform_decl = builder.GetMethodDecl(
      builder.GetAsciiString(transform_method_.c_str()),
      builder.GetProto(param_type, builder.GetTypeList({param_type})),
      builder.GetType(transform_class_.c_str()));
  auto param_transform_method = code_ir->Alloc<lir::Method>(
      param_transform_decl, param_transform_decl->orig_index);

  // Account for the 'this' pointer if we're non-static
  if ((ir_method->access_flags & dex::kAccStatic) == 0) {
    param_idx_++;
  }

  auto reg = code_ir->Alloc<lir::VReg>(regs - args_count + param_idx_);
  auto args = code_ir->Alloc<lir::VRegRange>(reg->reg, 1);

  auto get_flag = code_ir->Alloc<lir::Bytecode>();
  get_flag->opcode = dex::OP_INVOKE_STATIC_RANGE;
  get_flag->operands.push_back(args);
  get_flag->operands.push_back(param_transform_method);
  code_ir->instructions.InsertBefore(visitor.out, get_flag);

  auto mov_flag = code_ir->Alloc<lir::Bytecode>();
  mov_flag->opcode = dex::OP_MOVE_RESULT_OBJECT;
  mov_flag->operands.push_back(reg);
  code_ir->instructions.InsertBefore(visitor.out, mov_flag);

  return true;
}

void ModifyParameterTransform::Apply(
    std::shared_ptr<ir::DexFile> dex_ir) const {
  const std::string jni_name = "L" + GetClassName() + ";";

  slicer::MethodInstrumenter mi(dex_ir);
  ir::MethodId id(jni_name.c_str(), method_name_.c_str(),
                  method_signature_.c_str());
  mi.AddTransformation<ModifyParameter>(param_idx_, kHookClassName,
                                        transform_method_);
  if (!mi.InstrumentMethod(id)) {
    Log::V("ModifyParameterTransform failed: %s", jni_name.c_str());
  }
}

}  // namespace deploy