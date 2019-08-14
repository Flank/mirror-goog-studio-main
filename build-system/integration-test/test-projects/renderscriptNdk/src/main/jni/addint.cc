/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <ScriptC_addint.h>
#include <jni.h>
#include <rsCppStructs.h>

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_android_basicrenderscript_MainActivity_addint(
    JNIEnv* env, jclass thiz, jstring pCacheDir) {
  // Get cache directory
  const char* cacheDir = env->GetStringUTFChars(pCacheDir, nullptr);

  // Initialize RS
  sp<RS> renderScript = new RS();
  renderScript->init(cacheDir, 0);

  // Get script
  sp<ScriptC_addint> scriptPtr = new ScriptC_addint(renderScript);

  // Call rs method
  scriptPtr->invoke_addint(1, 2);

  // Cleanup
  env->ReleaseStringUTFChars(pCacheDir, cacheDir);
}
}