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

void ListExperiments(std::shared_ptr<ir::DexFile> dex_ir);

using Experiment = void (*)(std::shared_ptr<ir::DexFile>);

// the registry of available experiments
std::map<std::string, Experiment> experiments_registry = {
    { "list_experiments", &ListExperiments },
    { "full_rewrite", &FullRewrite },
    { "stress_entry_hook", &StressEntryHook },
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
