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

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

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
  JniClass live_literal_kt(jni_, klass);

  for (auto update : request.updates()) {
    jobject key = jni_->NewStringUTF(update.key().c_str());
    jvalue args[2];
    args[0].l = key;

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
