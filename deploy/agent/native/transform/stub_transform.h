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

#include <jvmti.h>

#include <memory>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"

namespace deploy {

class StubTransform : public Transform {
 public:
  StubTransform(const std::string& class_name) : Transform(class_name) {}

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) const override;

 private:
  const char* kFakeHook = "APPLYCHANGES";
  const char* kFakeHookClass = "LApply/Changes;";
};

class Print : public slicer::Transformation {
 public:
  virtual bool Apply(lir::CodeIr* code_ir) override;
};

class HookToStub : public slicer::Transformation {
 public:
  explicit HookToStub(const char* hook_name, const char* stub_class,
                      const char* stub_prefix)
      : hook_name_(hook_name),
        stub_class_(stub_class),
        stub_prefix_(stub_prefix) {}
  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  const char* hook_name_;
  const char* stub_class_;
  const char* stub_prefix_;

  void BuildCacheCheck(lir::CodeIr* code_ir, lir::Bytecode* first_instr,
                       lir::Bytecode* last_instr);
  void BuildStub(lir::CodeIr* code_ir, lir::Bytecode* invoke_static);
};

}  // namespace deploy