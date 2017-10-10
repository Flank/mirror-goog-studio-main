/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include <vector>
#include <unordered_map>
#include <jni.h>

static JNICALL jstring NativeToString(JNIEnv* env, jclass clazz, jobject o) {
  jclass obj_class = env->FindClass("java/lang/Object");
  jmethodID to_str_id = env->GetMethodID(obj_class, "toString",
                                         "()Ljava/lang/String;");
  jobject result = env->CallObjectMethod(o, to_str_id);
  return reinterpret_cast<jstring>(result);
}

static std::unordered_map<int, jobject> g_id_to_gref;
static JNICALL jint AllocateGlobalRef(JNIEnv* env, jclass clazz, jobject o) {
  static int last_id = 0;
  auto gref = env->NewGlobalRef(o);
  int new_id = ++last_id;
  g_id_to_gref[new_id] = gref;
  return new_id;
}

static JNICALL bool FreeGlobalRef(JNIEnv* env, jclass clazz, jint id) {
  auto it = g_id_to_gref.find(id);
  if (it == g_id_to_gref.end()) return false;
  env->DeleteGlobalRef(it->second);
  g_id_to_gref.erase(it);
  return true;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    std::vector<JNINativeMethod> methods {
      {(char*)"NativeToString",
       (char*)"(Ljava/lang/Object;)Ljava/lang/String;",
       (void *)NativeToString},
      {(char*)"AllocateGlobalRef",
       (char*)"(Ljava/lang/Object;)I",
       (void *)AllocateGlobalRef},
      {(char*)"FreeGlobalRef",
       (char*)"(I)Z",
       (void *)FreeGlobalRef},
    };

    jclass cls = env->FindClass((char*)"com/activity/NativeCodeActivity");
    env->RegisterNatives(cls, methods.data(), methods.size());

    return JNI_VERSION_1_6;
}
