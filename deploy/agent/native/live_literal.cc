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

namespace deploy {

proto::AgentLiveLiteralUpdateResponse LiveLiteral::Update(
    const proto::LiveLiteralUpdateRequest& request) {
  proto::AgentLiveLiteralUpdateResponse response;
  // TODO: For now just do a printf and the test will verify if we get to this
  // stage.
  jclass syscls = jni_->FindClass("java/lang/System");
  jfieldID fid = jni_->GetStaticFieldID(syscls, "out", "Ljava/io/PrintStream;");
  jobject out = jni_->GetStaticObjectField(syscls, fid);
  jclass pscls = jni_->FindClass("java/io/PrintStream");
  jmethodID mid = jni_->GetMethodID(pscls, "println", "(Ljava/lang/String;)V");
  jni_->CallVoidMethod(out, mid,
                       jni_->NewStringUTF("Live Literal Update on VM"));
  response.set_status(proto::AgentLiveLiteralUpdateResponse::OK);
  return response;
}

}  // namespace deploy
