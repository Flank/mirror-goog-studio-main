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

static JNICALL jstring NativeToString(JNIEnv* env, jclass clazz, jlong ref) {
  jclass obj_class = env->FindClass("java/lang/Object");
  jmethodID to_str_id = env->GetMethodID(obj_class, "toString",
                                         "()Ljava/lang/String;");
  jobject obj = reinterpret_cast<jobject>(ref);
  jobject result = env->CallObjectMethod(obj, to_str_id);
  return reinterpret_cast<jstring>(result);
}

static JNICALL jlong AllocateGlobalRef(JNIEnv* env, jclass clazz, jobject o) {
  jobject gref = env->NewGlobalRef(o);
  return reinterpret_cast<jlong>(gref);
}

static JNICALL void FreeGlobalRef(JNIEnv* env, jclass clazz, jlong ref) {
  env->DeleteGlobalRef(reinterpret_cast<jobject>(ref));
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    std::vector<JNINativeMethod> methods{
        {(char*)"NativeToString", (char*)"(J)Ljava/lang/String;",
         (void*)NativeToString},
        {(char*)"AllocateGlobalRef", (char*)"(Ljava/lang/Object;)J",
         (void*)AllocateGlobalRef},
        {(char*)"FreeGlobalRef", (char*)"(J)V", (void*)FreeGlobalRef},
    };

    jclass cls = env->FindClass((char*)"com/activity/NativeCodeActivity");
    env->RegisterNatives(cls, methods.data(), methods.size());

    return JNI_VERSION_1_6;
}
