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

#include "tools/base/deploy/agent/native/native_callbacks.h"

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/swapper.h"

namespace deploy {

bool RegisterNatives(JNIEnv* jni, const std::vector<NativeBinding>& bindings) {
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
  Swapper& swapper = Swapper::Instance();
  return swapper.FinishSwap(jni);
}

}  // namespace deploy
