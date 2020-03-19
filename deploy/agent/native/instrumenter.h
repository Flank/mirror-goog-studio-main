/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef INSTRUMENTER_H
#define INSTRUMENTER_H

#include <jvmti.h>

#include <memory>
#include <unordered_map>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/deploy/common/log.h"

using std::shared_ptr;
using std::string;
using std::unordered_map;

namespace deploy {

namespace {
// Converts a java-friendly class name into JNI format.
const std::string ToJniFormat(const std::string& class_name) {
  return "L" + class_name + ";";
}

// Visitor to extract bytecode instructions from an IR.
struct BytecodeConvertingVisitor : public lir::Visitor {
  lir::Bytecode* out = nullptr;
  bool Visit(lir::Bytecode* bytecode) {
    out = bytecode;
    return true;
  }
};

// Transform that sets a given parameter to zero before a method executes.
class ParameterSet : public slicer::Transformation {
 public:
  ParameterSet(dex::u4 param_idx) : param_idx_(param_idx) {}

  bool Apply(lir::CodeIr* code_ir) override {
    lir::Bytecode* bytecode = nullptr;
    for (auto instr : code_ir->instructions) {
      BytecodeConvertingVisitor visitor;
      instr->Accept(&visitor);
      bytecode = visitor.out;
      if (bytecode != nullptr) {
        break;
      }
    }
    if (bytecode == nullptr) {
      return false;
    }

    const auto ir_method = code_ir->ir_method;

    auto regs = ir_method->code->registers;
    auto args_count = ir_method->code->ins_count;
    auto target_reg = regs - args_count + param_idx_;

    auto op_const = code_ir->Alloc<lir::Bytecode>();
    op_const->opcode = dex::OP_CONST;
    op_const->operands.push_back(code_ir->Alloc<lir::VReg>(target_reg));
    op_const->operands.push_back(code_ir->Alloc<lir::Const32>(0));

    code_ir->instructions.InsertBefore(bytecode, op_const);
    return true;
  }

 private:
  dex::u4 param_idx_;
};
}  // namespace

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name, bool overlay_swap);

// Probably should be in a utility header, but also only used here.
class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

  virtual void* Allocate(size_t size) {
    unsigned char* alloc = nullptr;
    jvmti_->Allocate(size, &alloc);
    return (void*)alloc;
  }

  virtual void Free(void* ptr) {
    if (ptr == nullptr) {
      return;
    }

    jvmti_->Deallocate((unsigned char*)ptr);
  }

 private:
  jvmtiEnv* jvmti_;
};

class Transform {
 public:
  Transform(const std::string& class_name) : class_name_(class_name) {}
  virtual ~Transform() = default;

  std::string GetClassName() const { return class_name_; }
  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) const = 0;

 private:
  const std::string class_name_;
};

struct MethodHooks {
  const std::string method_name;
  const std::string method_signature;
  const std::string entry_hook;
  const std::string exit_hook;

  static const std::string kNoHook;

  MethodHooks(const std::string& method_name,
              const std::string& method_signature,
              const std::string& entry_hook, const std::string& exit_hook)
      : method_name(method_name),
        method_signature(method_signature),
        entry_hook(entry_hook),
        exit_hook(exit_hook) {}
};

class HookTransform : public Transform {
 public:
  HookTransform(const std::string& class_name, const std::string& method_name,
                const std::string& method_signature,
                const std::string& entry_hook, const std::string& exit_hook)
      : Transform(class_name) {
    hooks_.emplace_back(method_name, method_signature, entry_hook, exit_hook);
  }

  HookTransform(const std::string& class_name,
                const std::vector<MethodHooks>& hooks)
      : Transform(class_name), hooks_(hooks) {}

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) const override {
    for (const MethodHooks& hook : hooks_) {
      slicer::MethodInstrumenter mi(dex_ir);
      if (hook.entry_hook != MethodHooks::kNoHook) {
        const ir::MethodId entry_hook(kHookClassName, hook.entry_hook.c_str());
        mi.AddTransformation<slicer::EntryHook>(
            entry_hook, slicer::EntryHook::Tweak::ThisAsObject);
      }
      if (hook.exit_hook != MethodHooks::kNoHook) {
        const ir::MethodId exit_hook(kHookClassName, hook.exit_hook.c_str());
        mi.AddTransformation<slicer::ExitHook>(exit_hook);
      }
      const ir::MethodId target_method(ToJniFormat(GetClassName()).c_str(),
                                       hook.method_name.c_str(),
                                       hook.method_signature.c_str());
      if (!mi.InstrumentMethod(target_method)) {
        Log::E("Failed to instrument: %s", GetClassName().c_str());
      }
    }
  }

 private:
  const char* kHookClassName =
      "Lcom/android/tools/deploy/instrument/InstrumentationHooks;";
  std::vector<MethodHooks> hooks_;
};

class ParameterSetTransform : public Transform {
 public:
  ParameterSetTransform(
      const std::string& class_name,
      const std::unordered_map<std::string, std::string> target_methods)
      : Transform(class_name), target_methods_(target_methods) {}

  void Apply(std::shared_ptr<ir::DexFile> dex_ir) const override {
    slicer::MethodInstrumenter mi(dex_ir);
    // Currently hardcode that we set the first parameter to zero. We don't need
    // anything more complex at the moment.
    mi.AddTransformation<ParameterSet>(1);

    const std::string class_name = ToJniFormat(GetClassName());
    for (const auto& kv : target_methods_) {
      ir::MethodId target_method(class_name.c_str(),
                                 /* method name */ kv.first.c_str(),
                                 /* method signature*/ kv.second.c_str());
      if (!mi.InstrumentMethod(target_method)) {
        Log::E("Failed to instrument: %s", GetClassName().c_str());
      }
    }
  }

 private:
  const std::unordered_map<std::string, std::string> target_methods_;
};

}  // namespace deploy

#endif
