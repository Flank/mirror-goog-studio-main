/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include "tools/base/deploy/agent/native/live_literal.h"

#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

const char* kSupportClass =
    "com/android/tools/deploy/instrument/LiveLiteralSupport";

jstring LiveLiteral::LookUpKeyByOffSet(const std::string& helper, int offset) {
  // TODO: This method needs some TLC in terms of error reporting.
  // The current UX (under review) is built toward a "best effort" approach
  // with zero user feedback. So for now we are just going use Log::E().

  // Java:
  // Method[] results = Class.forName(helper).getDeclaredMethods();
  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(), helper);

  if (klass == nullptr) {
    jni_->ExceptionClear();
    Log::E("Cannot find Live Literal helper class: '%s'", helper.c_str());
    return nullptr;
  }

  jmethodID get_all_methods =
      jni_->GetMethodID(jni_->FindClass("java/lang/Class"),
                        "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");

  if (get_all_methods == nullptr) {
    // Almost impossible.
    Log::E("java.lang.Class.getDeclaredMethods does not exists");
    return nullptr;
  }

  // This can't be null but it could be empty.
  jobjectArray result =
      (jobjectArray)jni_->CallObjectMethod(klass, get_all_methods);
  jsize length = jni_->GetArrayLength(result);

  // Java:
  // for (Method func : result) {
  //   LiveLiteralInfo annotation = func.getAnnotation(LiveLiteralInfo.class);
  //   if (annotation != null) {
  //     ...
  //   }
  // }
  jclass info_class = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(),
      "androidx/compose/runtime/internal/LiveLiteralInfo");

  jclass method_class = jni_->FindClass("java/lang/reflect/Method");
  if (method_class == nullptr) {
    // Almost impossible.
    Log::E("java.lang.reflect.Method does not exists");
    return nullptr;
  }

  jmethodID get_annotation =
      jni_->GetMethodID(method_class, "getAnnotation",
                        "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;");

  if (get_annotation == nullptr) {
    // Almost impossible.
    Log::E("java.lang.reflect.Method.getAnnotation() does not exists");
    return nullptr;
  }

  jmethodID get_key =
      jni_->GetMethodID(info_class, "key", "()Ljava/lang/String;");
  if (get_key == nullptr) {
    // key() should be in the Compose API. Most likely we are out of sync
    // with the Compose compiler.
    Log::E("LiveLiteralInfo.key() does not exists");
    return nullptr;
  }

  jmethodID get_offset = jni_->GetMethodID(info_class, "offset", "()I");
  if (get_offset == nullptr) {
    // offset() should be in the Compose API. Most likely we are out of sync
    // with the Compose compiler.
    Log::E("LiveLiteralInfo.offset() does not exists");
    return nullptr;
  }

  // Return any key that matches the given offset.
  for (int i = 0; i < length; i++) {
    jobject func = jni_->GetObjectArrayElement(result, i);
    jobject annotation =
        jni_->CallObjectMethod(func, get_annotation, info_class);

    if (annotation != nullptr) {
      jstring key = (jstring)jni_->CallObjectMethod(annotation, get_key);
      jint cur_offset = jni_->CallIntMethod(annotation, get_offset);
      if (cur_offset == offset) {
        return key;
      }
    }
  }

  return nullptr;
}

proto::AgentLiveLiteralUpdateResponse LiveLiteral::Update(
    const proto::LiveLiteralUpdateRequest& request) {
  proto::AgentLiveLiteralUpdateResponse response;

  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(),
      "androidx/compose/runtime/internal/LiveLiteralKt");
  if (klass == nullptr) {
    jni_->ExceptionClear();
    Log::V(
        "LiveLiteralKt Not found. Not Starting JetPack Compose Live Literal "
        "Update");
  } else {
    Log::V("LiveLiteralKt found. Starting JetPack Compose Live Literal Update");
  }

  if (!InstrumentApplication(jvmti_, jni_, request.package_name(),
                             /*overlay_swap*/ false)) {
    response.set_status(
        proto::AgentLiveLiteralUpdateResponse::INSTRUMENTATION_FAILED);
    ErrEvent("Could not instrument application");
    return response;
  }

  // Call Support the support class enable() first.
  jvalue enable_args[1];
  enable_args[0].l = klass;
  JniClass support(jni_, kSupportClass);
  support.CallStaticMethod<void>({"enable", "(Ljava/lang/Class;)V"},
                                 enable_args);

  JniClass live_literal_kt(jni_, klass);

  for (auto update : request.updates()) {
    const std::string key = update.key();
    jobject jkey;
    if (key.empty()) {
      const std::string helper = update.helper_class();
      int offset = update.offset();
      jkey = LookUpKeyByOffSet(helper, offset);
    } else {
      jkey = jni_->NewStringUTF(key.c_str());
    }

    jvalue args[2];
    args[0].l = jkey;

    if (update.type() == "Ljava/lang/String;") {
      Log::V("Live Literal Update with String");
      jobject value = jni_->NewStringUTF(update.value().c_str());
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "C") {
      Log::V("Live Literal Update with Character");
      jclass string_class = jni_->FindClass("java/lang/String");
      jmethodID parse_char = jni_->GetMethodID(string_class, "charAt", "(I)C");
      jchar result = jni_->CallCharMethod(
          jni_->NewStringUTF(update.value().c_str()), parse_char, 0);

      jclass char_class = jni_->FindClass("java/lang/Character");
      jmethodID parse = jni_->GetStaticMethodID(char_class, "valueOf",
                                                "(C)Ljava/lang/Character;");
      jobject value = jni_->CallStaticObjectMethod(char_class, parse, result);
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "B") {
      Log::V("Live Literal Update with Byte");
      jclass byte_class = jni_->FindClass("java/lang/Byte");
      jmethodID parse = jni_->GetStaticMethodID(
          byte_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Byte;");
      jobject value = jni_->CallStaticObjectMethod(
          byte_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "I") {
      Log::V("Live Literal Update with Integer");
      jclass int_class = jni_->FindClass("java/lang/Integer");
      jmethodID parse = jni_->GetStaticMethodID(
          int_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Integer;");
      jobject value = jni_->CallStaticObjectMethod(
          int_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "J") {
      Log::V("Live Literal Update with Long");
      jclass long_class = jni_->FindClass("java/lang/Long");
      jmethodID parse = jni_->GetStaticMethodID(
          long_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Long;");
      jobject value = jni_->CallStaticObjectMethod(
          long_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "S") {
      Log::V("Live Literal Update with Short");
      jclass short_class = jni_->FindClass("java/lang/Short");
      jmethodID parse = jni_->GetStaticMethodID(
          short_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Short;");
      jobject value = jni_->CallStaticObjectMethod(
          short_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "F") {
      Log::V("Live Literal Update with Float");
      jclass float_class = jni_->FindClass("java/lang/Float");
      jmethodID parse = jni_->GetStaticMethodID(
          float_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Float;");
      jobject value = jni_->CallStaticObjectMethod(
          float_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "D") {
      Log::V("Live Literal Update with Double");
      jclass double_class = jni_->FindClass("java/lang/Double");
      jmethodID parse = jni_->GetStaticMethodID(
          double_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Double;");
      jobject value = jni_->CallStaticObjectMethod(
          double_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else if (update.type() == "Z") {
      Log::V("Live Literal Update with Boolean");
      jclass bool_class = jni_->FindClass("java/lang/Boolean");
      jmethodID parse = jni_->GetStaticMethodID(
          bool_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;");
      jobject value = jni_->CallStaticObjectMethod(
          bool_class, parse, jni_->NewStringUTF(update.value().c_str()));
      args[1].l = value;
      live_literal_kt.CallStaticMethod<void>(
          {"updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V"},
          args);

    } else {
      // TODO: Error Handling.
      Log::E("Live Literal Update with Unknown Type: %s",
             update.type().c_str());
    }
  }

  response.set_status(proto::AgentLiveLiteralUpdateResponse::OK);
  return response;
}

}  // namespace deploy
