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

namespace deploy {

// Provides a cache for the dex file output of jvmti class transforms, and
// allows for the retrieval of previously cached dex files keyed by class name.
class TransformCache {
 public:
  static TransformCache Create(const std::string& cache_path);

  bool ReadClass(const std::string& class_name,
                 std::vector<dex::u4>* class_bytes) const;
  bool WriteClass(const std::string& class_name,
                  const std::vector<dex::u4>& class_bytes) const;

 private:
  TransformCache(const std::string& cache_path) : cache_path_(cache_path) {}

  const std::string cache_path_;
  std::string GetCachePath(const std::string& class_name) const;
};

}  // namespace deploy

#endif
