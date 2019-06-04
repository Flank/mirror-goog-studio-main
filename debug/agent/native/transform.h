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

#ifndef TRANSFORM_H_
#define TRANSFORM_H_

#include <memory>
#include <string>

#include "slicer/dex_ir.h"

namespace debug {

// A dex bytecode transformation targeting a specific class.
class ClassTransform {
 public:
  ClassTransform(std::string class_desc) : class_desc_(class_desc){};

  virtual ~ClassTransform() = default;

  // Apply this transformation to the target class.
  //
  // Implementations may return false to abort instrumentation for the entire
  // class. This is useful if the transformation failed and [dex_ir] might now
  // be in an invalid state.
  virtual bool Apply(std::shared_ptr<ir::DexFile> dex_ir) = 0;

  const std::string& class_desc() const { return class_desc_; }

 private:
  const std::string class_desc_;
};

}  // namespace debug

#endif  // TRANSFORM_H_
