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

#include "tools/base/deploy/agent/native/transforms.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

TransformCache TransformCache::Create(const std::string& cache_path) {
  if (IO::access(cache_path, F_OK) && IO::mkdir(cache_path, S_IRWXU)) {
    Log::W("Could not create transform cache directory: %s %s", strerror(errno),
           cache_path.c_str());
  }
  return TransformCache(cache_path);
}

bool TransformCache::ReadClass(const std::string& class_name,
                               std::vector<dex::u4>* class_bytes) const {
  std::string path = GetCachePath(class_name);
  return deploy::ReadFile(path, class_bytes);
}

bool TransformCache::WriteClass(const std::string& class_name,
                                const std::vector<dex::u4>& class_bytes) const {
  const std::string& path = GetCachePath(class_name);
  return deploy::WriteFile(path, class_bytes);
}

std::string TransformCache::GetCachePath(const std::string& class_name) const {
  std::string file_name = class_name;
  std::replace(file_name.begin(), file_name.end(), '/', '-');
  return cache_path_ + "/" + file_name;
}

}  // namespace deploy