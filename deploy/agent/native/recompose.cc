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

#include "tools/base/deploy/agent/native/recompose.h"

#include <jni.h>
#include <jvmti.h>
#include <string.h>

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

// Can be null if the application isn't a JetPack Compose application.
jobject Recompose::GetComposeHotReload() const {
  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(), HOT_RELOADER_CLASS);
  if (klass == nullptr) {
    jni_->ExceptionClear();
    return nullptr;
  }
  Log::V("GetComposeHotReload found. Starting JetPack Compose HotReload");
  JniClass reloaderClass(jni_, klass);
  return reloaderClass.GetStaticObjectField("Companion", HOT_RELOADER_VMTYPE);
}

void Recompose::SaveStateAndDispose(jobject reloader) const {
  JniObject reloader_jnio(jni_, reloader);
  JniClass activity_thread(jni_, "android/app/ActivityThread");
  jobject context = activity_thread.CallStaticObjectMethod(
      "currentApplication", "()Landroid/app/Application;");
  reloader_jnio.CallVoidMethod("saveStateAndDispose", "(Ljava/lang/Object;)V",
                               context);

  if (jni_->ExceptionCheck()) {
    ErrEvent("Exception During SaveStateAndDispose");
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
  }
}

void Recompose::LoadStateAndCompose(jobject reloader) const {
  JniObject reloader_jnio(jni_, reloader);
  JniClass activity_thread(jni_, "android/app/ActivityThread");
  jobject context = activity_thread.CallStaticObjectMethod(
      "currentApplication", "()Landroid/app/Application;");
  reloader_jnio.CallVoidMethod("loadStateAndCompose", "(Ljava/lang/Object;)V",
                               context);

  if (jni_->ExceptionCheck()) {
    ErrEvent("Exception During loadStateAndCompose");
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
  }
}

}  // namespace deploy
