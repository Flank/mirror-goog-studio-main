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
#ifndef HOTSWAP_H
#define HOTSWAP_H

#include "jni.h"
#include "jvmti.h"

namespace swapper {

class HotSwap {
 public:
  HotSwap(jvmtiEnv* jvmti, JNIEnv* jni) : jvmti_(jvmti), jni_(jni) {}

  // Invokes JVMTI RedefineClasses with on all .dex files in the 'dir' and
  // delete them afterward.
  bool DoHotSwap(std::string& dir);

 private:
  // Invoke JVMTI RedefineClasses on the class with the given 'name' using the
  // content from 'location'
  bool RedefineClass(std::string& name, std::string& location);
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
};

}  // namespace swapper
#endif
