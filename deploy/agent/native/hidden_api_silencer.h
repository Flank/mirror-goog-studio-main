/*
 * Copyright (C) 2021 The Android Open Source Project
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
#ifndef HIDDEN_API_POLICY_H
#define HIDDEN_API_POLICY_H

#include <jvmti.h>

namespace deploy {

class HiddenAPISilencer {
 public:
  explicit HiddenAPISilencer(jvmtiEnv* jvmti);
  ~HiddenAPISilencer();

 private:
  jint policy_ = 0;
  jvmtiEnv* jvmti_ = nullptr;
  bool supported_ = false;

  bool Setup();

  jvmtiExtensionFunction DisableHiddenApiEnforcementPolicy = nullptr;
  jvmtiExtensionFunction GetHiddenApiEnforcementPolicy = nullptr;
  jvmtiExtensionFunction SetHiddenApiEnforcementPolicy = nullptr;

  void Free(void* obj);
};

}  // namespace deploy
#endif
