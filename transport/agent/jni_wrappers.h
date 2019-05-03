/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef AGENT_SUPPORT_JNI_TYPES_H_
#define AGENT_SUPPORT_JNI_TYPES_H_

#include <jni.h>
#include <memory>
#include <string>

// This file implements a collection of classes that make it easy to wrap
// JNI types, exposing C++ versions of their values and releasing JNI resources
// automatically.
namespace profiler {

// Wrap a jbyteArray, exposing it as a std::string of bytes
class JByteArrayWrapper {
 public:
  JByteArrayWrapper(JNIEnv *env, const jbyteArray &jbytes, const jint jlen) {
    char *bytes = new char[jlen];
    env->GetByteArrayRegion(jbytes, 0, jlen, reinterpret_cast<jbyte *>(bytes));
    byte_str_.assign(bytes, jlen);
    delete[] bytes;
  }

  // Note: Although this is technically returning a string, this really is more
  // of a vector<byte> array; however, we return std::string as an optimization
  // because that's how gRPC represents a byte array. Since this "string"
  // represents binary data, it can contain 0's inside of it.
  const std::string &get() const { return byte_str_; }

  const int32_t length() const { return byte_str_.length(); }

 private:
  std::string byte_str_;
};

// Wrap a jstring, exposing it as a std::string.
// If the jstring comes from a null object, it will be exposed as "".
class JStringWrapper {
 public:
  JStringWrapper(JNIEnv *env, const jstring &jstr) {
    if (jstr == nullptr) {
      str_ = "";
    } else {
      const char *c_str = env->GetStringUTFChars(jstr, NULL);
      if (c_str == nullptr) {
        str_ = "";
      } else {
        str_ = c_str;
      }
      env->ReleaseStringUTFChars(jstr, c_str);
    }
  }

  const std::string &get() const { return str_; }

 private:
  std::string str_;
};

}  // namespace profiler

#endif  // AGENT_SUPPORT_JNI_TYPES_H_
