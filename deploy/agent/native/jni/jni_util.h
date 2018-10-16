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

#ifndef JNI_UTIL_H
#define JNI_UTIL_H

#include <string>

#include <jni.h>
#include <jvmti.h>

using std::string;

namespace deploy {

// Sets the parameter jni to point to the current jni function table.
bool GetJni(JavaVM* vm, JNIEnv*& jni);

// Sets the parameter jvmti to point to the current jvmti function table.
bool GetJvmti(JavaVM* vm, jvmtiEnv*& jvmti);

// Gets an std::string from a jstring. Does not delete the jni local jstring.
string JStringToString(JNIEnv* jni, jstring str);

}  // namespace deploy

#endif