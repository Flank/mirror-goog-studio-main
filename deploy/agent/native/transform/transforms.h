/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef TRANSFORMS_H
#define TRANSFORMS_H

#include <jvmti.h>

#include <memory>

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"

namespace deploy {

class Transform {
 public:
  Transform(const std::string& class_name) : class_name_(class_name) {}
  virtual ~Transform() = default;

  std::string GetClassName() const { return class_name_; }
  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) const = 0;

 private:
  const std::string class_name_;
};

// Provides a cache for the dex file output of jvmti class transforms, and
// allows for the retrieval of previously cached dex files keyed by class name.
class TransformCache {
 public:
  explicit TransformCache(const std::string& cache_path = "UNINITIALIZED")
      : cache_path_(cache_path) {}
  virtual ~TransformCache() = default;
  virtual void Init();

  virtual bool ReadClass(const std::string& class_name,
                         std::vector<dex::u4>* class_bytes) const;
  virtual bool WriteClass(const std::string& class_name,
                          const std::vector<dex::u4>& class_bytes) const;

 private:
  const std::string cache_path_;
  std::string GetCachePath(const std::string& class_name) const;
};

class DisabledTransformCache : public TransformCache {
 public:
  explicit DisabledTransformCache() : TransformCache() {}
  virtual ~DisabledTransformCache() = default;
  void Init() override {}

  bool ReadClass(const std::string& class_name,
                 std::vector<dex::u4>* class_bytes) const override;
  bool WriteClass(const std::string& class_name,
                  const std::vector<dex::u4>& class_bytes) const override;
};

struct BytecodeConvertingVisitor : public lir::Visitor {
  lir::Bytecode* out = nullptr;
  bool Visit(lir::Bytecode* bytecode) {
    out = bytecode;
    return true;
  }
};

}  // namespace deploy

#endif
