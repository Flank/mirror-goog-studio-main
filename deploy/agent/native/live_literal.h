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
#ifndef LIVE_LITERAL_H
#define LIVE_LITERAL_H

#include <jni.h>
#include <jvmti.h>

#include "tools/base/deploy/agent/native/class_finder.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class LiveLiteral {
 public:
  LiveLiteral(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& package_name)
      : jvmti_(jvmti),
        jni_(jni),
        package_name_(package_name),
        class_finder_(jvmti, jni) {}
  proto::AgentLiveLiteralUpdateResponse Update(
      const proto::LiveLiteralUpdateRequest& request);
  static const char* kSupportClass;

 private:
  // Look up key name from the parse tree offset. The helper is a Compose
  // generated class able to work with information about the initial parse tree.
  jstring LookUpKeyByOffSet(const std::string& helper, int offset);

  // Instrument the LiveLiteral$FooBarKt helper class where the literals'
  // value resides. What we do is change the <clinit> of that class so
  // it reads a mapped value for some of the updated static fields.
  //
  // return non-empty error message if operation fails.
  std::string InstrumentHelper(const std::string& helper);

  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
  const std::string package_name_;
  ClassFinder class_finder_;
  proto::AgentLiveLiteralUpdateResponse response_;
};

}  // namespace deploy
#endif
