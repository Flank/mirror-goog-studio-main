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
 */
#ifndef PROFILER_TRANSFORM_H
#define PROFILER_TRANSFORM_H

#include "slicer/dex_ir.h"

namespace profiler {

// A class-level abstraction for handling bytecode instrumentation. Each
// subclass implementation should target one class even if multiple methods
// need to be transformed.
class Transform {
 public:
  Transform(const char* class_name) : class_name_(class_name){};
  virtual ~Transform() = default;

  // Apply transformations to the input |dex_ir|. Note that the input
  // can contain multiple classes so the contract here is quite loose.
  // It is up to the subclass implementatation to transform only the
  // class of interest.
  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) = 0;

  const char* GetClassName() { return class_name_.c_str(); }

 private:
  std::string class_name_;
};

}  // namespace profiler

#endif  // PROFILER_TRANSFORM_H
