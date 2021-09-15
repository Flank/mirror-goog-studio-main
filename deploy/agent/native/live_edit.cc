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

#include "tools/base/deploy/agent/native/live_edit.h"

#include <unordered_set>

#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/recompose.h"
#include "tools/base/deploy/agent/native/transform/stub_transform.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"
#include "tools/base/deploy/common/log.h"

// TODO: We need some global state that holds all these information
std::unordered_set<std::string> primed_classes;

namespace deploy {
proto::LiveEditResponse LiveEdit(jvmtiEnv* jvmti, JNIEnv* jni,
                                 const proto::LiveEditRequest& req) {
  proto::LiveEditResponse resp;

  if (SetUpInstrumentationJar(jvmti, jni, req.package_name()).empty()) {
    resp.set_status(proto::LiveEditResponse::INSTRUMENTATION_FAILED);
    resp.set_error_message("Could not set up instrumentation jar");
    return resp;
  }

  // class_name is in the format of "com.example.Target"
  if (primed_classes.find(req.class_name()) == primed_classes.end()) {
    TransformCache cache = DisabledTransformCache();
    Instrumenter instrumenter(jvmti, jni, cache, false);
    std::string name = req.class_name();

    // Transform expect class name to be in the format of "com/example/Target"
    std::replace(name.begin(), name.end(), '.', '/');
    const StubTransform stub(name);
    instrumenter.Instrument(stub);
    primed_classes.insert(req.class_name());
    Log::E("Live Edit priming %s", name.c_str());
  }

  const std::string code = req.class_data();
  jbyteArray arr = jni->NewByteArray(code.length());
  jni->SetByteArrayRegion(arr, 0, req.class_data().size(),
                          (jbyte*)req.class_data().data());

  JniClass stub(jni, "com/android/tools/deploy/instrument/LiveEditStubs");

  const std::string key = req.class_name() + "->" + req.method_signature();
  Log::E("Live Edit key %s", key.c_str());
  stub.CallStaticVoidMethod("addToCache", "(Ljava/lang/String;[B)V",
                            jni->NewStringUTF(key.c_str()), arr);

  Recompose recompose(jvmti, jni);
  jobject reloader = recompose.GetComposeHotReload();
  if (reloader) {
    jobject state = recompose.SaveStateAndDispose(reloader);
    recompose.LoadStateAndCompose(reloader, state);
  }

  resp.set_status(proto::LiveEditResponse::OK);
  return resp;
}
}  // namespace deploy
