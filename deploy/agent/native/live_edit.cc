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

namespace {
// The format expected for class_name is com/example/ClassName$InnerClass.
void PrimeClass(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& class_name) {
  if (primed_classes.find(class_name) == primed_classes.end()) {
    TransformCache cache = DisabledTransformCache();
    Instrumenter instrumenter(jvmti, jni, cache, false);

    const StubTransform stub(class_name);
    instrumenter.Instrument(stub);
    primed_classes.insert(class_name);

    Log::V("Live Edit primed %s", class_name.c_str());
  }
}
}  // namespace

proto::LiveEditResponse LiveEdit(jvmtiEnv* jvmti, JNIEnv* jni,
                                 const proto::LiveEditRequest& req) {
  proto::LiveEditResponse resp;

  if (SetUpInstrumentationJar(jvmti, jni, req.package_name()).empty()) {
    resp.set_status(proto::LiveEditResponse::INSTRUMENTATION_FAILED);
    resp.set_error_message("Could not set up instrumentation jar");
    return resp;
  }

  const auto& target_class = req.target_class();
  PrimeClass(jvmti, jni, target_class.class_name());

  const auto& target_code = target_class.class_data();
  jbyteArray arr = jni->NewByteArray(target_code.size());
  jni->SetByteArrayRegion(arr, 0, target_code.size(),
                          (jbyte*)target_code.data());

  JniClass stub(jni, "com/android/tools/deploy/liveedit/LiveEditStubs");

  auto app_loader = ClassFinder(jvmti, jni).GetApplicationClassLoader();
  stub.CallStaticVoidMethod("init", "(Ljava/lang/ClassLoader;)V", app_loader);

  const std::string key =
      target_class.class_name() + "->" + target_class.method_signature();
  Log::V("Live Edit key %s", key.c_str());
  stub.CallStaticVoidMethod("addToCache", "(Ljava/lang/String;[B)V",
                            jni->NewStringUTF(key.c_str()), arr);

  for (auto support_class : req.support_classes()) {
    const auto& support_code = support_class.class_data();
    jbyteArray arr = jni->NewByteArray(support_code.size());
    jni->SetByteArrayRegion(arr, 0, support_code.size(),
                            (jbyte*)support_code.data());
    stub.CallStaticVoidMethod("addProxiedClass", "([B)V", arr);
    PrimeClass(jvmti, jni, support_class.class_name());
  }

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
