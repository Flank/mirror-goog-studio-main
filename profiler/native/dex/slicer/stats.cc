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

#include "stats.h"
#include "dex_ir.h"

#include <stdio.h>

namespace slicer {

Stats stats = {};
Perf perf = {};

// Print .dex IR stats
void PrintDexIrStats(std::shared_ptr<ir::DexFile> dexIr, bool useCsv) {
  if(useCsv) {
      // TODO
      return;
  }
  printf("\nIR statistics:\n");
  printf("  strings                        : %zu\n", dexIr->strings.size());
  printf("  types                          : %zu\n", dexIr->types.size());
  printf("  protos                         : %zu\n", dexIr->protos.size());
  printf("  fields                         : %zu\n", dexIr->fields.size());
  printf("  encoded_fields                 : %zu\n", dexIr->encoded_fields.size());
  printf("  methods                        : %zu\n", dexIr->methods.size());
  printf("  encoded_methods                : %zu\n", dexIr->encoded_methods.size());
  printf("  classes                        : %zu\n", dexIr->classes.size());
  printf("  type_lists                     : %zu\n", dexIr->type_lists.size());
  printf("  code                           : %zu\n", dexIr->code.size());
  printf("  debug_info                     : %zu\n", dexIr->debug_info.size());
  printf("  encoded_values                 : %zu\n", dexIr->encoded_values.size());
  printf("  encoded_arrays                 : %zu\n", dexIr->encoded_arrays.size());
  printf("  annotations                    : %zu\n", dexIr->annotations.size());
  printf("  annotation_elements            : %zu\n", dexIr->annotation_elements.size());
  printf("  annotation_sets                : %zu\n", dexIr->annotation_sets.size());
  printf("  annotation_set_ref_lists       : %zu\n", dexIr->annotation_set_ref_lists.size());
  printf("  annotations_directories        : %zu\n", dexIr->annotations_directories.size());
  printf("  field_annotations              : %zu\n", dexIr->field_annotations.size());
  printf("  method_annotations             : %zu\n", dexIr->method_annotations.size());
  printf("  param_annotations              : %zu\n", dexIr->param_annotations.size());
  printf("\n");
}

}  // namespace slicer
