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

#pragma once

#include "code_ir.h"
#include "common.h"
#include "dex_ir.h"
#include "dex_ir_builder.h"

#include <memory>
#include <vector>
#include <utility>

namespace slicer {

// Interface for a single transformation operation
class Transformation {
 public:
  virtual ~Transformation() = default;
  virtual bool Apply(lir::CodeIr* code_ir) = 0;
};

// Insert a call to the "entry hook" at the start of the instrumented method:
// The "entry hook" will be forwarded the original incoming arguments plus
// an explicit "this" argument for non-static methods.
class EntryHook : public Transformation {
 public:
  EntryHook(const ir::MethodId& hook_method_id) : hook_method_id_(hook_method_id) {
    // hook method signature is generated automatically
    CHECK(hook_method_id_.signature == nullptr);
  }

  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  ir::MethodId hook_method_id_;
};

// Insert a call to the "exit hook" method before every return
// in the instrumented method. The "exit hook" will be passed the
// original return value and it may return a new return value.
class ExitHook : public Transformation {
 public:
  ExitHook(const ir::MethodId& hook_method_id) : hook_method_id_(hook_method_id) {
    // hook method signature is generated automatically
    CHECK(hook_method_id_.signature == nullptr);
  }

  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  ir::MethodId hook_method_id_;
};

// Replace every invoke-virtual[/range] to the a specified method with
// a invoke-static[/range] to the detour method. The detour is a static
// method which takes the same arguments as the original method plus
// an explicit "this" argument, and returns the same type as the original method
class DetourVirtualInvoke : public Transformation {
 public:
  DetourVirtualInvoke(const ir::MethodId& orig_method_id, const ir::MethodId& detour_method_id)
    : orig_method_id_(orig_method_id), detour_method_id_(detour_method_id) {
    // detour method signature is automatically created
    // to match the original method and must not be explicitly specified
    CHECK(detour_method_id_.signature == nullptr);
  }

  virtual bool Apply(lir::CodeIr* code_ir) override;

 private:
  ir::MethodId orig_method_id_;
  ir::MethodId detour_method_id_;
};

// A friendly helper for instrumenting existing methods: it allows batching
// a set of transformations to be applied to method (the batching allow it
// to build and encode the code IR once per method regardless of how many
// transformation are applied)
//
// For example, if we want to add both entry and exit hooks to a
// Hello.Test(int) method, the code would look like this:
//
//    ...
//    slicer::MethodInstrumenter mi(dex_ir);
//    mi.AddTransformation<slicer::EntryHook>(ir::MethodId("LTracer;", "OnEntry"));
//    mi.AddTransformation<slicer::ExitHook>(ir::MethodId("LTracer;", "OnExit"));
//    CHECK(mi.InstrumentMethod(ir::MethodId("LHello;", "Test", "(I)I")));
//    ...
//
class MethodInstrumenter {
 public:
  MethodInstrumenter(std::shared_ptr<ir::DexFile> dex_ir) : dex_ir_(dex_ir) {}

  // No copy/move semantics
  MethodInstrumenter(const MethodInstrumenter&) = delete;
  MethodInstrumenter& operator=(const MethodInstrumenter&) = delete;

  // Queue a transformation
  // (T is a class derived from Transformation)
  template<class T, class... Args>
  void AddTransformation(Args&&... args) {
    transformations_.emplace_back(new T(std::forward<Args>(args)...));
  }

  // Apply all the queued transformations to the specified method
  bool InstrumentMethod(const ir::MethodId& method_id);

 private:
  std::shared_ptr<ir::DexFile> dex_ir_;
  std::vector<std::unique_ptr<Transformation>> transformations_;
};

}  // namespace slicer
