/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "tools/base/debug/agent/native/async-stack/inject_hooks.h"

#include <cstdlib>

#include "slicer/instrumentation.h"
#include "tools/base/debug/agent/native/log.h"

namespace debug {

namespace {

// These class names and method names must be kept in sync with IntelliJ.
const char* kCaptureStorageDesc =
    "Lcom/intellij/rt/debugger/agent/CaptureStorage;";

const char* kCaptureHook = "capture";
const char* kInsertEnterHook = "insertEnter";
const char* kInsertExitHook = "insertExit";

}  // namespace

bool InjectionPoint::Apply(std::shared_ptr<ir::DexFile> dex_ir) {
  // Apply instrumentation to all methods with the correct name.
  bool found = false;
  for (auto& method : dex_ir->encoded_methods) {
    const char* clazz = method->decl->parent->descriptor->c_str();
    const char* name = method->decl->name->c_str();
    if (class_desc().compare(clazz) != 0 ||
        method_name_.compare(name) != 0 ||
        method->code == nullptr) {
      continue;
    }

    found = true;
    auto sig = method->decl->prototype->Signature();
    lir::CodeIr ir(method.get(), dex_ir);
    AsyncStackTransform transform(&ir, kind_, *capture_key_);

    if (transform.Apply()) {
      ir.Assemble();
      Log::V("Async stack instrumentation applied to %s %s%s", clazz, name,
             sig.c_str());
    } else {
      Log::E("Failed to apply async stack instrumentation to %s %s%s (%s)",
             clazz, name, sig.c_str(), transform.error().c_str());
      return false;
    }
  }

  if (!found) {
    Log::E("Async stack: could not find method %s in class %s",
           method_name_.c_str(), class_desc().c_str());
    // We still return true in this case, because [dex_ir]
    // is still in a valid state.
  }

  return true;
}

AsyncStackTransform::AsyncStackTransform(lir::CodeIr* ir, InjectionKind kind,
                                         const CaptureKey& capture_key)
    : ir_(ir),
      kind_(kind),
      capture_key_(capture_key),
      orig_method_start_(*ir_->instructions.begin()) {
  auto code = ir_->ir_method->code;
  if (code != nullptr) {
    orig_ins_start_ = code->registers - code->ins_count;
  }
}

bool AsyncStackTransform::Apply() {
  if (!CheckValid()) {
    return false;
  }
  if (!AllocateScratchRegs()) {
    return false;
  }
  InjectEntryHook();
  if (kind_ == kInsert) {
    InjectExitHook();
  }
  return true;
}

bool AsyncStackTransform::CheckValid() {
  if (ir_->ir_method->code == nullptr || ir_->instructions.empty()) {
    error_ = "Expected nonempty method body";
    return false;
  }

  if (ir_->ir_method->access_flags & dex::kAccConstructor) {
    // TODO: "An instance initializer must call another instance
    // initializer (same class or superclass) before any instance
    // members can be accessed."
    error_ = "Constructor injection points not yet supported";
    return false;
  }

  if (!capture_key_.CheckValid(this)) {
    return false;
  }

  return true;
}

bool ReceiverKey::CheckValid(AsyncStackTransform* t) const {
  if (t->ir_->ir_method->access_flags & dex::kAccStatic) {
    t->error_ = "Used `this` as capture key in static method";
    return false;
  }
  return true;
}

bool ParamKey::CheckValid(AsyncStackTransform* t) const {
  auto param_type_list = t->ir_->ir_method->decl->prototype->param_types;
  if (param_type_list == nullptr) {
    t->error_ = "Used parameter key in method with no parameters";
    return false;
  }

  auto& param_types = param_type_list->types;
  if (param_ >= param_types.size()) {
    t->error_ = "Parameter index is out of bounds";
    return false;
  }

  if (param_types[param_]->GetCategory() != ir::Type::Category::Reference) {
    t->error_ = "Capture key must be an object";
    return false;
  }

  return true;
}

bool AsyncStackTransform::AllocateScratchRegs() {
  // Note: we disable register renumbering in order to simplify our bookkeeping.
  // In particular, if renumbering were allowed then it would be harder to
  // know where to find method arguments.
  slicer::AllocateScratchRegs regalloc(kNumScratchRegs_, /*renumber*/ false);
  if (!regalloc.Apply(ir_)) {
    error_ = "Failed to allocate scratch registers";
    return false;
  }

  auto& regs = regalloc.ScratchRegs();
  for (auto reg : regs) {
    if (reg >= kMaxRegsSupported_) {
      error_ = "Methods with this many registers not yet supported";
      return false;
    }
  }

  auto it = regs.begin();
  scratch_ = *it++;
  exn_stash_ = *it++;

  return true;
}

void AsyncStackTransform::InjectEntryHook() {
  auto pos = orig_method_start_;
  auto hook_name = kind_ == kCapture ? kCaptureHook : kInsertEnterHook;
  auto entry_hook = BuildHookReference(hook_name);
  auto arg = capture_key_.ComputeReg(this);
  auto invoke = BuildHookInvoke(entry_hook, arg);
  ir_->instructions.InsertBefore(pos, invoke);
}

void AsyncStackTransform::InjectExitHook() {
  auto exit_hook = BuildHookReference(kInsertExitHook);

  // Invokes the exit hook before [pos].
  auto invoke_exit_hook = [&](lir::Instruction* pos) {
    // TODO: The insertExit() hook uses the capture key only for logging
    // purposes, so to simplify things we just pass null for now.
    auto load_null = ir_->Alloc<lir::Bytecode>();
    load_null->opcode = dex::OP_CONST_16;
    load_null->operands.push_back(ir_->Alloc<lir::VReg>(scratch_));
    load_null->operands.push_back(ir_->Alloc<lir::Const32>(0));
    ir_->instructions.InsertBefore(pos, load_null);

    auto invoke = BuildHookInvoke(exit_hook, scratch_);
    ir_->instructions.InsertBefore(pos, invoke);
  };

  // Invoke the insert exit hook at all method return points.
  for (auto insn : ir_->instructions) {
    if (auto bytecode = dynamic_cast<lir::Bytecode*>(insn)) {
      auto flags = dex::GetFlagsFromOpcode(bytecode->opcode);
      if (flags & dex::kReturn) {
        invoke_exit_hook(/*pos*/ insn);
      }
    }
  }

  // Create a finally-block that intercepts all exceptions.
  auto finally = ir_->Alloc<lir::Label>(0);
  RedirectAllExceptions(finally);
  ir_->instructions.push_back(finally);

  // Save the in-flight exception for later.
  auto exn = ir_->Alloc<lir::VReg>(exn_stash_);
  auto move_exn = ir_->Alloc<lir::Bytecode>();
  move_exn->opcode = dex::OP_MOVE_EXCEPTION;
  move_exn->operands.push_back(exn);
  ir_->instructions.push_back(move_exn);

  // Invoke exit hook and then rethrow.
  invoke_exit_hook(/*pos*/ *ir_->instructions.end());
  auto rethrow = ir_->Alloc<lir::Bytecode>();
  rethrow->opcode = dex::OP_THROW;
  rethrow->operands.push_back(exn);
  ir_->instructions.push_back(rethrow);
}

void AsyncStackTransform::RedirectAllExceptions(lir::Label* finally) {
  // Try-blocks must be non-overlapping, so we cannot simply wrap the
  // entire method with a catch-all try-block. Instead, we install a
  // catch-all handler in all existing try-blocks and create new
  // try-blocks to cover the gaps between.

  // Returns whether there are bytecode instructions in the range [begin, end).
  // Used to ensure that we do not create empty try-blocks.
  auto contains_bytecode = [](lir::Instruction* begin, lir::Instruction* end) {
    for (auto it = begin; it != end; it = it->next) {
      if (dynamic_cast<lir::Bytecode*>(it)) {
        return true;
      }
    }
    return false;
  };

  // Wraps bytecode between the [prev] and [next] try-blocks with a try-block.
  // Handles the cases where [prev] or [next] are null.
  auto cover_gap = [&](lir::TryBlockEnd* prev, lir::TryBlockBegin* next) {
    auto gap_begin = prev != nullptr ? prev->next : orig_method_start_;
    auto gap_end = next != nullptr ? next : *ir_->instructions.end();
    if (!contains_bytecode(gap_begin, gap_end)) {
      return;  // Try-block ranges are required to be nonempty.
    }
    auto try_begin = ir_->Alloc<lir::TryBlockBegin>();
    auto try_end = ir_->Alloc<lir::TryBlockEnd>();
    try_end->try_begin = try_begin;
    try_end->catch_all = finally;
    ir_->instructions.InsertBefore(gap_begin, try_begin);
    ir_->instructions.InsertBefore(gap_end, try_end);
  };

  // Install catch-all handlers and fill all gaps.
  lir::TryBlockEnd* prev_end = nullptr;
  for (auto insn : ir_->instructions) {
    if (auto try_end = dynamic_cast<lir::TryBlockEnd*>(insn)) {
      cover_gap(prev_end, try_end->try_begin);
      if (try_end->catch_all == nullptr) {
        try_end->catch_all = finally;
      }
      prev_end = try_end;
    }
  }
  cover_gap(prev_end, nullptr);
}

dex::u4 ReceiverKey::ComputeReg(AsyncStackTransform* t) const {
  // "Instance methods are passed a this reference as their first argument."
  return t->orig_ins_start_;
}

dex::u4 ParamKey::ComputeReg(AsyncStackTransform* t) const {
  // "The N arguments to a method land in the last N registers of the method's
  // invocation frame, in order. Wide arguments consume two registers.
  // Instance methods are passed a this reference as their first argument."
  auto& param_types = t->ir_->ir_method->decl->prototype->param_types->types;
  bool is_static = t->ir_->ir_method->access_flags & dex::kAccStatic;
  dex::u4 reg_idx = is_static ? 0 : 1;
  for (dex::u4 i = 0; i < param_; ++i) {
    auto category = param_types[i]->GetCategory();
    reg_idx += category == ir::Type::Category::WideScalar ? 2 : 1;
  }
  return t->orig_ins_start_ + reg_idx;
}

lir::Method* AsyncStackTransform::BuildHookReference(const char* name) {
  // All three hook methods have the same signature: (Ljava/lang/Object;)V
  ir::Builder builder(ir_->dex_ir);
  auto ascii_name = builder.GetAsciiString(name);
  auto proto = builder.GetProto(
      builder.GetType("V"),
      builder.GetTypeList({builder.GetType("Ljava/lang/Object;")}));
  auto clazz = builder.GetType(kCaptureStorageDesc);
  auto decl = builder.GetMethodDecl(ascii_name, proto, clazz);
  return ir_->Alloc<lir::Method>(decl, decl->orig_index);
}

lir::Bytecode* AsyncStackTransform::BuildHookInvoke(lir::Method* hook,
                                                    dex::u4 arg) {
  // We use invoke-static/range so that we don't have to worry about
  // the register number being small enough.
  auto invoke = ir_->Alloc<lir::Bytecode>();
  invoke->opcode = dex::OP_INVOKE_STATIC_RANGE;
  invoke->operands.push_back(ir_->Alloc<lir::VRegRange>(arg, 1));
  invoke->operands.push_back(hook);
  return invoke;
}

}  // namespace debug
