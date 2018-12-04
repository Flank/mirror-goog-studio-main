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
 *
 */

#ifndef DEX_VERIFY_H
#define DEX_VERIFY_H

#include <memory>
#include <string>
#include <vector>

#include <jvmti.h>

#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

struct ClassInfo {
  const std::string class_name;
  const unsigned char* class_data;
  const size_t class_data_len;
  const jclass klass;
};

// Given a list of ClassInfo objects representing new dex bytecode, uses JVMTI's
// RetransformClasses and ClassFileLoadHook methods to compare the new class
// definitions with the already loaded definitions. Incompatible changes are
// recorded and returned via the error_details vector.
//
// Currently only checks for added/removed fields using field names, which is
// enough to support validation of inner R classes. Field types and access
// modifiers are NOT examined.
void CheckForClassErrors(jvmtiEnv* jvmti,
                         const std::vector<ClassInfo>& classes_to_verify,
                         std::vector<proto::JvmtiErrorDetails>* error_details);

}  // namespace deploy

#endif
