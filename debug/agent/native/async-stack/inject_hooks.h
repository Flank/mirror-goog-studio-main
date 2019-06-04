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

// This file supports asynchronous stacktraces in the debugger by injecting
// hooks at capture points (to store the current stacktrace) and at
// insertion points (to recover a saved stacktrace).
//
// Definitions
//
//   Callback: Any piece of code that is asynchronously scheduled for future
//   execution---usually a lambda or a custom java.lang.Thread instance.
//
//   Capture point: The point at which the callback is scheduled, and thus where
//   the current stacktrace should be captured for later use.
//
//   Insertion point: The point at which the callback starts executing, and thus
//   where the previously captured stacktrace should be matched with the
//   currently executing callback.
//
// At capture points we inject a single call to CaptureStorage.capture(...)
// at the top of the method.
//
// At insertion points we inject a call to CaptureStorage.insertEnter(...)
// at the top of the method, and then we also inject a call to
// CaptureStorage.insertExit(...) at all method exits. In order to ensure
// that the exit hook gets called even when an exception is thrown, we
// also wrap the entire method in a try-finally block.
//
// Note: you can find the code for CaptureStorage in the IntelliJ platform at
// com.intellij.rt.debugger.agent.CaptureStorage
//
// See http://go/studio-async-stacks for more info.

#ifndef ASYNC_STACK_TRANSFORM_H_
#define ASYNC_STACK_TRANSFORM_H_

#include <memory>
#include <string>

#include "slicer/code_ir.h"
#include "slicer/dex_format.h"
#include "slicer/dex_ir.h"
#include "tools/base/debug/agent/native/transform.h"

namespace debug {

class AsyncStackTransform;

// Distinguishes between a capture point and an insertion point.
enum InjectionKind { kCapture, kInsert };

// In order to track stacktraces across asynchronously scheduled callbacks,
// a "capture key" is used to match a capture point with its corresponding
// insertion point. A capture key is a Java object that is in scope at both
// injection points, and it is passed as an argument to every CaptureStorage
// hook invocation. It could be a method parameter, a field, or `this`.
class CaptureKey {
 public:
  virtual ~CaptureKey() = default;

  // Returns true iff this capture key is valid for the given transform.
  // E.g., returns false if using a `this` capture key in a static method.
  virtual bool CheckValid(AsyncStackTransform* t) const = 0;

  // Given a method currently being transformed, returns
  // the register which holds the capture key at method entry.
  virtual dex::u4 ComputeReg(AsyncStackTransform* t) const = 0;
};

// `this` capture key.
class ReceiverKey : public CaptureKey {
 public:
  virtual bool CheckValid(AsyncStackTransform* t) const override;
  virtual dex::u4 ComputeReg(AsyncStackTransform* t) const override;
};

// Parameter capture key.
class ParamKey : public CaptureKey {
 public:
  ParamKey(dex::u4 param) : param_(param) {}
  virtual bool CheckValid(AsyncStackTransform* t) const override;
  virtual dex::u4 ComputeReg(AsyncStackTransform* t) const override;

 private:
  const dex::u4 param_;  // Parameter index starting from 0.
};

// TODO: Implement FieldKey.

// An injection point can be either a capture point or an insertion point.
// Injection points are registered with the agent so that it knows which
// classes and methods to transform.
class InjectionPoint : public ClassTransform {
 public:
  InjectionPoint(std::string class_desc, std::string method_name,
                 InjectionKind kind,
                 std::unique_ptr<const CaptureKey> capture_key)
      : ClassTransform(class_desc),
        method_name_(method_name),
        kind_(kind),
        capture_key_(std::move(capture_key)) {}

  // Applies AsyncStackTransform to the appropriate methods in [dex_ir].
  virtual bool Apply(std::shared_ptr<ir::DexFile> dex_ir) override;

 private:
  const std::string method_name_;
  const InjectionKind kind_;
  const std::unique_ptr<const CaptureKey> capture_key_;
};

// Instruments a single method associated with an injection point.
// Example usage:
// ```
// CodeIr ir(...);
// ReceiverKey capture_key;
// AsyncStackTransform t(&ir, kInsert, capture_key);
// if (t.Apply()) {
//   ir.Assemble();
// } else {
//   Log::E("%s", t.error().c_str());
// }
// ```
class AsyncStackTransform {
 public:
  AsyncStackTransform(lir::CodeIr* ir, InjectionKind kind,
                      const CaptureKey& capture_key);

  // Applies instrumentation to the method; returns true iff successful.
  bool Apply();

  // If Apply() failed, this method returns an error message explaining why.
  const std::string& error() { return error_; }

 private:
  // Returns true iff this transformation is valid. An example *invalid*
  // transformation would be using a `this` capture key in a static method.
  bool CheckValid();

  // Returns true iff successful.
  bool AllocateScratchRegs();

  // Injects a call to either CaptureStorage.capture() or
  // CaptureStorage.insertEnter() at the beginning of the method.
  void InjectEntryHook();

  // Injects a call to CaptureStorage.insertExit() at all method exits,
  // even if the method exits by exception.
  void InjectExitHook();

  // Conceptually, wraps the entire method in a try-finally block.
  void RedirectAllExceptions(lir::Label* finally);

  // Creates a method reference for the specified hook in CaptureStorage.
  lir::Method* BuildHookReference(const char* name);

  // Creates bytecode which invokes a CaptureStorage hook with one argument.
  lir::Bytecode* BuildHookInvoke(lir::Method* hook, dex::u4 arg);

  lir::CodeIr* ir_;
  const InjectionKind kind_;
  const CaptureKey& capture_key_;
  std::string error_;

  // Scratch registers are allocated at the beginning of Apply() and then
  // used throughout the transformation.
  //
  // The [scratch_] reg is used when loading the argument for a hook.
  // The [exn_stash_] reg is used to store an in-flight exception.
  //
  // TODO: We currently limit scratch register indices to be less than
  // 256, as many instructions do not support higher registers. Transformations
  // needing >256 registers will fail (gracefully). To remove this limitation we
  // would need to implement some register spilling logic.
  static constexpr dex::u4 kMaxRegsSupported_ = 1 << 8;
  static constexpr dex::u4 kNumScratchRegs_ = 2;  // Keep in sync.
  dex::u4 scratch_;
  dex::u4 exn_stash_;

  // slicer::AllocateScratchRegs will insert move instructions at
  // the top of the method to move arguments into lower registers in order to
  // make room for new scratch registers. This means that the scratch registers
  // allocated are not available for use until *after* those move instructions.
  // Thus we have to keep track of that boundary, as well as the original
  // start of the method input registers.
  lir::Instruction* orig_method_start_;
  dex::u4 orig_ins_start_;

  // The logic for loading the capture key is tightly coupled with the
  // rest of the transformation, so the capture keys are our friends.
  friend class ReceiverKey;
  friend class ParamKey;
};

}  // namespace debug

#endif  // ASYNC_STACK_TRANSFORM_H_
