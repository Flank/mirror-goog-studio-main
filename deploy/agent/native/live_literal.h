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
  LiveLiteral(jvmtiEnv* jvmti, JNIEnv* jni)
      : jvmti_(jvmti), jni_(jni), class_finder_(jvmti, jni) {}
  proto::AgentLiveLiteralUpdateResponse Update(
      const proto::LiveLiteralUpdateRequest& request);

 private:
  ClassFinder class_finder_;
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
};

}  // namespace deploy
#endif
