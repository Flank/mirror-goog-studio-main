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
    } else {
      // TODO: Strings for now.
      Log::V("Live Literal Update with Unknown Type");
    }
  }

  response.set_status(proto::AgentLiveLiteralUpdateResponse::OK);
  return response;
}

}  // namespace deploy
