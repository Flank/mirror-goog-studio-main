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

#include "experimental.h"
#include "slicer/code_ir.h"
#include "slicer/dex_ir.h"
#include "slicer/dex_ir_builder.h"

#include <string.h>
#include <map>
#include <memory>
#include <vector>

namespace experimental {

// Rewrites every method through raising to code IR -> back to bytecode
void FullRewrite(std::shared_ptr<ir::DexFile> dex_ir) {
  for (auto& ir_method : dex_ir->encoded_methods) {
    if (ir_method->code != nullptr) {
      lir::CodeIr code_ir(ir_method.get(), dex_ir);
      code_ir.Assemble();
    }
  }
}

// For every method body in the .dex image, replace invoke-virtual[/range]
// instances with a invoke-static[/range] to a fictitious Tracer.WrapInvoke(<args...>)
// WrapInvoke() is a static method which takes the same arguments as the
// original method plus an explicit "this" argument, and returns the same
// type as the original method.
void StressWrapInvoke(std::shared_ptr<ir::DexFile> dex_ir) {
  for (auto& ir_method : dex_ir->encoded_methods) {
    if (ir_method->code == nullptr) {
      continue;
    }

    lir::CodeIr code_ir(ir_method.get(), dex_ir);
    ir::Builder builder(dex_ir);

    // search for invoke-virtual[/range] bytecodes
    //
    // NOTE: we may be removing the current bytecode
    //   from the instructions list so we must use a
    //   different iteration style (it++ is done before
    //   handling *it, not after as in a normal iteration)
    //
    auto it = code_ir.instructions.begin();
    while (it != code_ir.instructions.end()) {
      auto instr = *it++;
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

      // construct the wrapper method declaration
      std::vector<ir::Type*> param_types;
      param_types.push_back(orig_method->parent);
      if (orig_method->prototype->param_types != nullptr) {
        const auto& orig_param_types = orig_method->prototype->param_types->types;
        param_types.insert(param_types.end(), orig_param_types.begin(), orig_param_types.end());
      }

      auto ir_proto = builder.GetProto(orig_method->prototype->return_type,
                                       builder.GetTypeList(param_types));

      auto ir_method_decl = builder.GetMethodDecl(builder.GetAsciiString("WrapInvoke"),
                                                  ir_proto,
                                                  builder.GetType("LTracer;"));

      auto wraper_method = code_ir.Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

      // new call bytecode
      auto new_call = code_ir.Alloc<lir::Bytecode>();
      new_call->opcode = new_call_opcode;
      new_call->operands.push_back(bytecode->operands[0]);
      new_call->operands.push_back(wraper_method);
      code_ir.instructions.InsertBefore(bytecode, new_call);

      // remove the old call bytecode
      //
      // NOTE: we can mutate the original bytecode directly
      //  since the instructions can't have multiple references
      //  in the code IR, but for testing purposes we'll do it
      //  the hard way here
      //
      code_ir.instructions.Remove(bytecode);
    }

    code_ir.Assemble();
  }
}

// For every method in the .dex image, insert an "entry hook" call
// to a fictitious method: Tracer.OnEntry(<args...>). OnEntry() has the
// same argument types as the instrumented method plus an explicit
// "this" for non-static methods. On entry to the instumented method
// we'll call OnEntry() with the values of the incoming arguments.
//
// NOTE: the entry hook will forward all the incoming arguments
//   so we need to define an Tracer.OnEntry overload for every method
//   signature. This means that for very large .dex images, approaching
//   the 64k method limit, we might not be able to allocate new method declarations.
//   (which is ok, and a good test case, since this is a stress scenario)
//
void StressEntryHook(std::shared_ptr<ir::DexFile> dex_ir) {
  for (auto& ir_method : dex_ir->encoded_methods) {
    if (ir_method->code == nullptr) {
      continue;
    }

    lir::CodeIr code_ir(ir_method.get(), dex_ir);
    ir::Builder builder(dex_ir);

    // 1. construct call target
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

    auto ir_method_decl = builder.GetMethodDecl(builder.GetAsciiString("OnEntry"),
                                                ir_proto,
                                                builder.GetType("LTracer;"));

    auto target_method = code_ir.Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

    // 2. argument registers
    auto regs = ir_method->code->registers;
    auto args_count = ir_method->code->ins_count;
    auto args = code_ir.Alloc<lir::VRegRange>(regs - args_count, args_count);

    // 3. call bytecode
    auto call = code_ir.Alloc<lir::Bytecode>();
    call->opcode = dex::OP_INVOKE_STATIC_RANGE;
    call->operands.push_back(args);
    call->operands.push_back(target_method);

    // 4. insert the hook before the first bytecode
    for (auto instr : code_ir.instructions) {
      auto bytecode = dynamic_cast<lir::Bytecode*>(instr);
      if (bytecode == nullptr) {
        continue;
      }
      code_ir.instructions.InsertBefore(bytecode, call);
      break;
    }

    code_ir.Assemble();
  }
}

// For every method in the .dex image, insert an "exit hook" call
// to a fictitious method: Tracer.OnExit(<return value...>).
// OnExit() is called right before returning from the instrumented
// method (on the non-exceptional path) and it will be passed the return
// value, if any. For non-void return types, the return value from OnExit()
// will also be used as the return value of the instrumented method.
void StressExitHook(std::shared_ptr<ir::DexFile> dex_ir) {
  for (auto& ir_method : dex_ir->encoded_methods) {
    if (ir_method->code == nullptr) {
      continue;
    }

    lir::CodeIr code_ir(ir_method.get(), dex_ir);
    ir::Builder builder(dex_ir);

    // do we have a void-return method?
    bool return_void =
        ::strcmp(ir_method->decl->prototype->return_type->descriptor->c_str(), "V") == 0;

    // 1. construct call target
    std::vector<ir::Type*> param_types;
    if (!return_void) {
      param_types.push_back(ir_method->decl->prototype->return_type);
    }

    auto ir_proto = builder.GetProto(ir_method->decl->prototype->return_type,
                                     builder.GetTypeList(param_types));

    auto ir_method_decl = builder.GetMethodDecl(builder.GetAsciiString("OnExit"),
                                                ir_proto,
                                                builder.GetType("LTracer;"));

    auto target_method = code_ir.Alloc<lir::Method>(ir_method_decl, ir_method_decl->orig_index);

    // 2. find and instrument the return instructions
    for (auto instr : code_ir.instructions) {
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

      // the call bytecode
      auto args = code_ir.Alloc<lir::VRegRange>(reg, reg_count);
      auto call = code_ir.Alloc<lir::Bytecode>();
      call->opcode = dex::OP_INVOKE_STATIC_RANGE;
      call->operands.push_back(args);
      call->operands.push_back(target_method);
      code_ir.instructions.InsertBefore(bytecode, call);

      // move result back to the right register
      //
      // NOTE: we're reusing the original return's operand,
      //   which is valid and more efficient than allocating
      //   a new LIR node, but it's also fragile: we need to be
      //   very careful about mutating shared nodes.
      //
      if (move_result_opcode != dex::OP_NOP) {
        auto move_result = code_ir.Alloc<lir::Bytecode>();
        move_result->opcode = move_result_opcode;
        move_result->operands.push_back(bytecode->operands[0]);
        code_ir.instructions.InsertBefore(bytecode, move_result);
      }
    }

    code_ir.Assemble();
  }
}

void ListExperiments(std::shared_ptr<ir::DexFile> dex_ir);

using Experiment = void (*)(std::shared_ptr<ir::DexFile>);

// the registry of available experiments
std::map<std::string, Experiment> experiments_registry = {
    { "list_experiments", &ListExperiments },
    { "full_rewrite", &FullRewrite },
    { "stress_entry_hook", &StressEntryHook },
    { "stress_exit_hook", &StressExitHook },
    { "stress_wrap_invoke", &StressWrapInvoke },
};

// Lists all the registered experiments
void ListExperiments(std::shared_ptr<ir::DexFile> dex_ir) {
  printf("\nAvailable experiments:\n");
  printf("-------------------------\n");
  for (auto& e : experiments_registry) {
    printf("  %s\n", e.first.c_str());
  }
  printf("-------------------------\n\n");
}

// Driver for running experiments
void Run(const char* experiment, std::shared_ptr<ir::DexFile> dex_ir) {
  auto it = experiments_registry.find(experiment);
  if (it == experiments_registry.end()) {
    printf("\nUnknown experiment '%s'\n", experiment);
    ListExperiments(dex_ir);
    exit(1);
  }

  printf("\nRunning experiment '%s' ... \n", experiment);
  (*it->second)(dex_ir);
}

}  // namespace experimental
