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

#include "native_callbacks.h"

#include "capabilities.h"
#include "config.h"
#include "hotswap.h"
#include "jni/jni_class.h"
#include "jni/jni_util.h"

namespace swapper {

bool RegisterNatives(JNIEnv* jni, const vector<NativeBinding>& bindings) {
  for (auto& binding : bindings) {
    jclass klass = jni->FindClass(binding.class_name);

    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
      return false;
    }

    jni->RegisterNatives(klass, &binding.native_method, 1);
    jni->DeleteLocalRef(klass);
  }

  return true;
}

int Native_GetAppInfoChanged(JNIEnv* jni, jobject object) {
  JniClass activity_thread_h(jni, "android/app/ActivityThread$H");
  return activity_thread_h.GetStaticField<jint>(
      {"APPLICATION_INFO_CHANGED", "I"});
}

bool Native_TryRedefineClasses(JNIEnv* jni, jobject object) {
  JavaVM* vm;
  if (jni->GetJavaVM(&vm) != 0) {
    return false;
  }

  jvmtiEnv* jvmti;
  if (!GetJvmti(vm, jvmti)) {
    return false;
  }

  HotSwap code_swap(jvmti, jni);

  // TODO(noahz): Prevent us from creating two JVMTI instances.
  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    return false;
  }

  const Config& config = Config::GetInstance();
  bool success = code_swap.DoHotSwap(config.GetSwapRequest());
  jvmti->RelinquishCapabilities(&REQUIRED_CAPABILITIES);
  return success;
}

}  // namespace swapper
