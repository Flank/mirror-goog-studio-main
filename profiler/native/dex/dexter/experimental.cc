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

#include <map>
#include <memory>

namespace experimental {

// Rewrites every method through raising to code IR -> back to bytecode
void FullRewrite(std::shared_ptr<ir::DexFile> dex_ir) {
  for (auto& ir_enc_method : dex_ir->encoded_methods) {
    if (ir_enc_method->code != nullptr) {
      lir::CodeIr code_ir(ir_enc_method.get(), dex_ir);
      code_ir.Assemble();
    }
  }
}

void ListExperiments(std::shared_ptr<ir::DexFile> dex_ir);

using Experiment = void (*)(std::shared_ptr<ir::DexFile>);

// the registry of available experiments
std::map<std::string, Experiment> experiments_registry = {
    { "list_experiments", &ListExperiments },
    { "full_rewrite", &FullRewrite },
};

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
