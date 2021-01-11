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

#include "tools/base/deploy/agent/native/hidden_api_silencer.h"

#include <string>

#include "tools/base/deploy/agent/native/jvmti/android.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

HiddenAPISilencer::HiddenAPISilencer(jvmtiEnv* jvmti) : jvmti_(jvmti) {
  supported_ = Setup();
  if (!supported_) {
    Log::T("JVMTI::HiddenAPIWarning:Suppressing not supported");
    return;
  }

  Log::T("JVMTI::HiddenAPIWarning:Suppressing");
  GetHiddenApiEnforcementPolicy(jvmti_, &policy_);
  DisableHiddenApiEnforcementPolicy(jvmti_);
}

HiddenAPISilencer::~HiddenAPISilencer() {
  if (!supported_) {
    Log::T("JVMTI::HiddenAPIWarning:Restoring not supported");
    return;
  }

  Log::T("JVMTI::HiddenAPIWarning:Restoring");
  SetHiddenApiEnforcementPolicy(jvmti_, policy_);
}

void HiddenAPISilencer::Free(void* obj) {
  jvmti_->Deallocate((unsigned char*)obj);
}

bool HiddenAPISilencer::Setup() {
  jint count = 0;
  jvmtiExtensionFunctionInfo* extensions = NULL;

  if (jvmti_->GetExtensionFunctions(&count, &extensions) != JVMTI_ERROR_NONE) {
    return false;
  }

  /* Find the JVMTI extension event we want */
  jvmtiExtensionFunctionInfo* extension = extensions;
  for (jint i = 0; i < count; i++, extension++) {
    if (android::jvmti::kGetFuncKey == extension->id) {
      GetHiddenApiEnforcementPolicy = extension->func;
    } else if (android::jvmti::kSetFuncKey == extension->id) {
      SetHiddenApiEnforcementPolicy = extension->func;
    } else if (android::jvmti::kDisFuncKey == extension->id) {
      DisableHiddenApiEnforcementPolicy = extension->func;
    }
  }

  // Clean up
  extension = extensions;
  for (jint i = 0; i < count; i++, extension++) {
    for (auto j = 0; j < extension->param_count; j++) {
      Free(extension->params[j].name);
    }
    Free(extension->short_description);
    Free(extension->errors);
    Free(extension->id);
    Free(extension->params);
  }
  Free(extensions);

  return SetHiddenApiEnforcementPolicy && GetHiddenApiEnforcementPolicy &&
         DisableHiddenApiEnforcementPolicy;
}

}  // namespace deploy