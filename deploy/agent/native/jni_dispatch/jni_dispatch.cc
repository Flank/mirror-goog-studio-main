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

#include <jni.h>

#include <string>

// This *must* match
// tools/base/deploy/agent/runtime/src/main/java/com/android/tools/deploy/liveedit/AndroidEval.java
constexpr int32_t kNoUnbox = 0;
constexpr int32_t kUnboxBool = 1 << 0;
constexpr int32_t kUnboxByte = 1 << 1;
constexpr int32_t kUnboxChar = 1 << 2;
constexpr int32_t kUnboxShort = 1 << 3;
constexpr int32_t kUnboxInt = 1 << 4;
constexpr int32_t kUnboxLong = 1 << 5;
constexpr int32_t kUnboxFloat = 1 << 6;
constexpr int32_t kUnboxDouble = 1 << 7;

namespace deploy {
namespace {
void ThrowIllegalStateException(JNIEnv* env, const char* text) {
  const char* class_name = "java/lang/IllegalStateException";
  jclass clazz = env->FindClass(class_name);
  env->ThrowNew(clazz, text);
}

const std::string GetClassName(JNIEnv* env, jclass cls) {
  jclass clazz = env->FindClass("java/lang/Class");
  jmethodID mid = env->GetMethodID(clazz, "getName", "()Ljava/lang/String;");
  jstring name = (jstring)env->CallObjectMethod(cls, mid);
  const char* string = env->GetStringUTFChars(name, 0);
  const std::string ret(string);
  env->ReleaseStringUTFChars(name, string);
  return ret;
}

bool CheckClass(JNIEnv* env, jclass expected, jobject obj) {
  if (!env->IsInstanceOf(obj, expected)) {
    std::string msg = "Unbox excepted ";
    msg += GetClassName(env, expected);
    msg += " but got ";
    msg += GetClassName(env, env->GetObjectClass(obj));
    ThrowIllegalStateException(env, msg.c_str());
    return false;
  }
  return true;
}

#define Unbox(METHOD_NAME, JAVA_TYPE, JAVA_METHOD, JAVA_DESC, JNI_TYPE, \
              JNI_METHOD)                                               \
  inline bool METHOD_NAME(JNIEnv* env, jobject obj, JNI_TYPE* v) {      \
    jclass cls = env->FindClass(JAVA_TYPE);                             \
    if (!CheckClass(env, cls, obj)) {                                   \
      return false;                                                     \
    }                                                                   \
    jmethodID method = env->GetMethodID(cls, JAVA_METHOD, JAVA_DESC);   \
    *v = env->JNI_METHOD(obj, method);                                  \
    return true;                                                        \
  }

Unbox(ToBool, "java/lang/Boolean", "booleanValue", "()Z", jboolean,
      CallBooleanMethod);
Unbox(ToChar, "java/lang/Character", "charValue", "()C", jchar, CallCharMethod);
Unbox(ToByte, "java/lang/Byte", "byteValue", "()B", jbyte, CallByteMethod);
Unbox(ToShort, "java/lang/Short", "shortValue", "()S", jshort, CallShortMethod);
Unbox(ToInt, "java/lang/Integer", "intValue", "()I", jint, CallIntMethod);
Unbox(ToLong, "java/lang/Long", "longValue", "()J", jlong, CallLongMethod);
Unbox(ToFloat, "java/lang/Float", "floatValue", "()F", jfloat, CallFloatMethod);
Unbox(ToDouble, "java/lang/Double", "doubleValue", "()D", jdouble,
      CallDoubleMethod);

class CallInfo {
 public:
  CallInfo(JNIEnv* env, jclass cls, jstring method, jstring desc,
           jobjectArray args, jintArray unbox)
      : env_(env),
        cls_(cls),
        methodName_(method),
        methodDesc_(desc),
        args_(args),
        unboxArray_(unbox) {}

  // If something fails, returns true and throw an exception.
  bool Get() {
    method_name_ = env_->GetStringUTFChars(methodName_, kDontCopy);
    method_desc_ = env_->GetStringUTFChars(methodDesc_, kDontCopy);
    unboxes_ = env_->GetIntArrayElements(unboxArray_, kDontCopy);

    if (!GetMethodID()) {
      return false;
    }

    if (!PrepareArguments()) {
      return false;
    }
    return true;
  }

  ~CallInfo() {
    delete[] values_;
    env_->ReleaseStringUTFChars(methodName_, method_name_);
    env_->ReleaseStringUTFChars(methodDesc_, method_desc_);
    env_->ReleaseIntArrayElements(unboxArray_, unboxes_, JNI_ABORT);
  }

  bool GetMethodID() {
    mid_ = env_->GetMethodID(cls_, method_name_, method_desc_);
    if (mid_ == nullptr) {
      // An error was already thrown, no need to throw another one.
      return false;
    }
    return true;
  }

  // Return false if an exception was thrown.
  bool PrepareArguments() {
    int num_unbox = env_->GetArrayLength(unboxArray_);
    int num_args = env_->GetArrayLength(args_);
    if (num_args != num_unbox) {
      std::string msg("Error: '");
      msg += method_name_;
      msg += method_desc_;
      msg += "' args size '";
      msg += std::to_string(num_args) + " does not match unbox size '";
      msg += std::to_string(num_unbox);
      msg += "'";
      ThrowIllegalStateException(env_, msg.c_str());
      return false;
    }

    values_ = new jvalue[num_args];
    for (int i = 0; i < num_args; i++) {
      jobject arg = env_->GetObjectArrayElement(args_, i);
      jint unboxType = unboxes_[i];
      switch (unboxType) {
        case kUnboxBool: {
          if (!ToBool(env_, arg, &values_[i].z)) {
            return false;
          }
          break;
        }
        case kUnboxInt: {
          if (!ToInt(env_, arg, &values_[i].i)) {
            return false;
          }
          break;
        }
        case kUnboxChar: {
          if (!ToChar(env_, arg, &values_[i].c)) {
            return false;
          }
          break;
        }
        case kUnboxByte: {
          if (!ToByte(env_, arg, &values_[i].b)) {
            return false;
          }
          break;
        }
        case kUnboxShort: {
          if (!ToShort(env_, arg, &values_[i].s)) {
            return false;
          }
          break;
        }
        case kUnboxLong: {
          if (!ToLong(env_, arg, &values_[i].j)) {
            return false;
          }
          break;
        }
        case kUnboxFloat: {
          if (!ToFloat(env_, arg, &values_[i].f)) {
            return false;
          }
          break;
        }
        case kUnboxDouble: {
          if (!ToDouble(env_, arg, &values_[i].d)) {
            return false;
          }
          break;
        }
        case kNoUnbox:
          values_[i].l = arg;
          break;
        default: {
          std::string msg("JNI_INTERPRETER: Unexpected unboxing value '");
          msg += std::to_string(unboxType) + "' for ";
          msg += method_name_;
          msg += method_desc_;
          ThrowIllegalStateException(env_, msg.c_str());
          return false;
        }
      }
    }
    return true;
  }

  jmethodID methodID() { return mid_; }

  jvalue* values() { return values_; }

 private:
  static constexpr jboolean* kDontCopy = nullptr;

  JNIEnv* env_ = nullptr;
  jmethodID mid_ = nullptr;
  jclass cls_ = nullptr;

  jstring methodName_ = nullptr;
  const char* method_name_ = nullptr;

  jstring methodDesc_ = nullptr;
  const char* method_desc_ = nullptr;

  jobjectArray args_ = nullptr;
  jvalue* values_ = nullptr;

  jintArray unboxArray_ = nullptr;
  jint* unboxes_ = nullptr;
};
}  // namespace
}  // namespace deploy

using namespace deploy;

extern "C" {
JNIEXPORT jobject JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialL(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return nullptr;
  return env->CallNonvirtualObjectMethodA(obj, cls, info.methodID(),
                                          info.values());
}

JNIEXPORT void JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecial(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return;
  return env->CallNonvirtualVoidMethodA(obj, cls, info.methodID(),
                                        info.values());
}

JNIEXPORT jint JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialI(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0;
  jint i =
      env->CallNonvirtualIntMethodA(obj, cls, info.methodID(), info.values());
  return i;
}

JNIEXPORT jshort JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialS(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0;
  return env->CallNonvirtualShortMethodA(obj, cls, info.methodID(),
                                         info.values());
}

JNIEXPORT jbyte JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialB(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0;
  return env->CallNonvirtualByteMethodA(obj, cls, info.methodID(),
                                        info.values());
}

JNIEXPORT jboolean JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialZ(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return JNI_FALSE;
  jboolean b = env->CallNonvirtualBooleanMethodA(obj, cls, info.methodID(),
                                                 info.values());
  return b;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialJ(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0;
  return env->CallNonvirtualLongMethodA(obj, cls, info.methodID(),
                                        info.values());
}

JNIEXPORT jfloat JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialF(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0.0f;
  return env->CallNonvirtualFloatMethodA(obj, cls, info.methodID(),
                                         info.values());
}

JNIEXPORT jdouble JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialD(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0.0;
  return env->CallNonvirtualDoubleMethodA(obj, cls, info.methodID(),
                                          info.values());
}

JNIEXPORT jchar JNICALL
Java_com_android_tools_deploy_interpreter_JNI_invokespecialC(
    JNIEnv* env, jclass, jobject obj, jclass cls, jstring method, jstring desc,
    jobjectArray args, jintArray unbox) {
  CallInfo info(env, cls, method, desc, args, unbox);
  if (!info.Get()) return 0;
  return env->CallNonvirtualCharMethodA(obj, cls, info.methodID(),
                                        info.values());
}

JNIEXPORT void JNICALL
Java_com_android_tools_deploy_interpreter_JNI_enterMonitor(JNIEnv* env, jclass,
                                                           jobject obj) {
  if (obj == nullptr) {
    ThrowIllegalStateException(env, "Cannot enter monitor with null object");
  }
  env->MonitorEnter(obj);
}

JNIEXPORT void JNICALL
Java_com_android_tools_deploy_interpreter_JNI_exitMonitor(JNIEnv* env, jclass,
                                                          jobject obj) {
  if (obj == nullptr) {
    ThrowIllegalStateException(env, "Cannot exit monitor with null object");
  }
  env->MonitorExit(obj);
}

}  // extern "C"
