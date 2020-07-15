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

#ifndef DEPLOYER_PATCH_APPLIER_H
#define DEPLOYER_PATCH_APPLIER_H

#include "tools/base/deploy/proto/deploy.pb.h"

#include <string>

namespace deploy {

class PatchApplier {
 public:
  PatchApplier(const std::string& root_directory)
      : root_directory_(root_directory) {}
  ~PatchApplier() = default;
  bool ApplyPatchToFD(const proto::PatchInstruction& patch, int dst_fd) const
      noexcept;

 private:
  std::string root_directory_;
};

}  // namespace deploy
#endif  // DEPLOYER_PATCH_APPLIER_H
