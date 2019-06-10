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

#include <cassert>
#include <cstdlib>
#include <fstream>
#include <functional>
#include <ios>
#include <vector>

#include "slicer/code_ir.h"
#include "slicer/dex_format.h"
#include "slicer/dex_ir.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/debug/agent/native/async-stack/inject_hooks.h"
#include "tools/base/debug/agent/native/log.h"

using namespace debug;

namespace {

void TestInjection(lir::CodeIr* ir, InjectionKind kind,
                   const CaptureKey& key_provider) {
  AsyncStackTransform t(ir, kind, key_provider);
  if (t.Apply()) {
    ir->Assemble();
  } else {
    auto decl = ir->ir_method->decl;
    auto clazz = decl->parent->descriptor;
    auto method = decl->name;
    auto sig = decl->prototype->Signature();
    Log::E("Instrumentation failed for %s %s%s (%s)", clazz->c_str(),
           method->c_str(), sig.c_str(), t.error().c_str());
    std::exit(1);
  }
}

// Convenience method that
// (1) builds the IR for [image],
// (3) passes each method to [consumer], and
// (3) creates a new dex image with the transformed IR.
void TransformEachMethod(const std::vector<dex::u1>& image,
                         std::function<void(lir::CodeIr*)> consumer) {
  dex::Reader reader(image.data(), image.size());
  reader.CreateFullIr();
  auto dex_ir = reader.GetIr();
  auto& methods = dex_ir->encoded_methods;

  for (auto& method : methods) {
    if (method->code == nullptr) {
      continue;  // Ignore methods with no body.
    }
    auto flags = method->access_flags;
    if (flags & dex::kAccConstructor) {
      continue;  // TODO: Constructors not yet supported.
    }

    lir::CodeIr ir(method.get(), dex_ir);
    consumer(&ir);
  }

  struct Allocator : public dex::Writer::Allocator {
    virtual void* Allocate(size_t size) { return ::malloc(size); }
    virtual void Free(void* ptr) { ::free(ptr); }
  };

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  Allocator allocator;

  dex::Writer writer(dex_ir);
  new_image = writer.CreateImage(&allocator, &new_image_size);
  if (new_image == nullptr) {
    Log::E("Writing new image failed");
    std::exit(1);
  }

  // TODO: Ideally we would also run a dex verifier on the output image.
  // For example, we could run ART's dex2oat tool with the flag
  // --compiler-filter=verify. Unfortunately, dex2oat depends on the
  // boot image and cannot really be run as a standalone tool. We may
  // have to do dex verification on the emulator.

  allocator.Free(new_image);
}

// Transform [ir] with a `this` key provider.
void TransformWithReceiverKey(lir::CodeIr* ir, InjectionKind kind) {
  if (ir->ir_method->access_flags & dex::kAccStatic) {
    return;
  }
  TestInjection(ir, kind, ReceiverKey());
}

// Transform [ir] with a parameter key provider.
void TransformWithParamKey(lir::CodeIr* ir, InjectionKind kind) {
  auto param_type_list = ir->ir_method->decl->prototype->param_types;
  if (param_type_list == nullptr) {
    return;
  }

  std::vector<dex::u4> objs;
  for (dex::u4 i = 0, e = param_type_list->types.size(); i < e; ++i) {
    auto type = param_type_list->types[i];
    if (type->GetCategory() == ir::Type::Category::Reference) {
      objs.push_back(i);
    }
  }
  if (objs.empty()) {
    return;
  }

  auto param_idx = objs[std::rand() % objs.size()];
  TestInjection(ir, kind, ParamKey(param_idx));
}

std::vector<dex::u1> ReadDexFile(const char* filename) {
  static_assert(sizeof(dex::u1) == sizeof(char), "Assumed char to be 8 bits");

  std::ifstream in(filename, std::ifstream::binary | std::ifstream::ate);
  if (!in) {
    Log::E("Failed to open .dex file: %s", filename);
    std::exit(1);
  }

  Log::I("Reading .dex file: %s", filename);
  auto size = in.tellg();
  std::vector<dex::u1> image(size);
  in.seekg(0);
  in.read((char*)image.data(), size);

  return image;
}

void TestWellFormedTransforms(const std::vector<dex::u1>& image) {
  Log::I("Running well-formed transformations and asserting that they succeed");

  int count = 0;

  for (auto kind : {kCapture, kInsert}) {
    auto kind_desc = kind == kCapture ? "capture" : "insert";

    Log::I("Injecting %s hooks with `this` key provider", kind_desc);
    TransformEachMethod(image, [&](lir::CodeIr* ir) {
      TransformWithReceiverKey(ir, kind);
      ++count;
    });

    Log::I("Injecting %s hooks with parameter key provider", kind_desc);
    TransformEachMethod(image, [&](lir::CodeIr* ir) {
      TransformWithParamKey(ir, kind);
      ++count;
    });
  }

  assert(count > 0);
  Log::I("There were %d successful injections", count);
}

void TestRandomTransforms(const std::vector<dex::u1>& image) {
  Log::I(
      "Running random (possibly malformed) transformations and "
      "asserting that we do not crash");

  int successes = 0;
  int failures = 0;

  TransformEachMethod(image, [&](lir::CodeIr* ir) {
    auto kind = rand() % 2 ? kCapture : kInsert;

    std::unique_ptr<CaptureKey> key_provider;
    if (rand() % 2) {
      key_provider.reset(new ReceiverKey());
    } else {
      key_provider.reset(new ParamKey(rand() % 10));
    }

    AsyncStackTransform t(ir, kind, *key_provider);
    if (t.Apply()) {
      ir->Assemble();
      ++successes;
    } else {
      ++failures;
    }
  });

  assert(failures > 0);
  Log::I("There were %d successes and %d (expected) failures", successes,
         failures);
}

}  // namespace

int main(int argc, char** argv) {
  if (argc != 2) {
    Log::E("Expected one parameter: dex-file");
    return 1;
  }

  std::srand(1);
  std::vector<dex::u1> image = ReadDexFile(argv[1]);

  TestWellFormedTransforms(image);
  TestRandomTransforms(image);

  Log::I("Stress test finished");
  return 0;
}
