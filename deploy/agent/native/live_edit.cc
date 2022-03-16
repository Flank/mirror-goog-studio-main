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
#include "tools/base/deploy/agent/native/live_edit_dex.h"
#include "tools/base/deploy/agent/native/recompose.h"
#include "tools/base/deploy/agent/native/transform/stub_transform.h"
#include "tools/base/deploy/agent/native/transform/transforms.h"
#include "tools/base/deploy/common/log.h"

// TODO: We need some global state that holds all these information
namespace {
std::unordered_set<std::string> primed_classes;
}

namespace deploy {

namespace {
// The format expected for class_name is com/example/ClassName$InnerClass.
void PrimeClass(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& class_name) {
  if (primed_classes.find(class_name) == primed_classes.end()) {
    auto cache = std::make_unique<DisabledTransformCache>();
    Instrumenter instrumenter(jvmti, jni, std::move(cache), false);

    const StubTransform stub(class_name);
    instrumenter.Instrument(stub);
    primed_classes.insert(class_name);

    Log::V("Live Edit primed %s", class_name.c_str());
  }
}

void UpdateClassBytecode(JNIEnv* jni, JniClass* live_edit_stubs,
                         const std::string& internal_name,
                         const std::string& bytecode, bool isProxyClass) {
  jstring class_name = jni->NewStringUTF(internal_name.c_str());
  jbyteArray bytecode_arr = jni->NewByteArray(bytecode.size());
  jni->SetByteArrayRegion(bytecode_arr, 0, bytecode.size(),
                          (jbyte*)bytecode.data());
  live_edit_stubs->CallStaticVoidMethod("addClass", "(Ljava/lang/String;[BZ)V",
                                        class_name, bytecode_arr, isProxyClass);
}
void SetDebugMode(JNIEnv* jni, bool debugMode) {
  jni->ExceptionClear();
  JniClass clazz(jni, "com/android/tools/deploy/interpreter/Config");
  if (!clazz.isValid()) {
    return;
  }

  jobject ins = clazz.CallStaticObjectMethod(
      "getInstance", "()Lcom/android/tools/deploy/interpreter/Config;",
      debugMode);
  if (ins == nullptr) {
    return;
  }

  JniObject instance = JniObject(jni, ins);
  jboolean mode = debugMode;
  instance.CallVoidMethod("setDebugMode", "(Z)V", mode);

  // Make sure we have not triggered something bad.
  if (jni->ExceptionCheck()) {
    jni->ExceptionClear();
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

  auto app_loader = ClassFinder(jvmti, jni).GetApplicationClassLoader();

  // Add the LiveEdit dex library to the application classloader.
  if (!SetUpLiveEditDex(jvmti, jni, req.package_name())) {
    resp.set_status(proto::LiveEditResponse::LAMBDA_DEX_LOAD_FAILED);
    resp.set_error_message("Could not set up live edit dex");
    return resp;
  }

  SetDebugMode(jni, req.debugmodeenabled());

  JniClass live_edit_stubs(jni,
                           "com/android/tools/deploy/liveedit/LiveEditStubs");
  live_edit_stubs.CallStaticVoidMethod("init", "(Ljava/lang/ClassLoader;)V",
                                       app_loader);

  const auto& target_class = req.target_class();
  UpdateClassBytecode(jni, &live_edit_stubs, target_class.class_name(),
                      target_class.class_data(), /* isProxyClass */ false);
  PrimeClass(jvmti, jni, target_class.class_name());

  for (auto support_class : req.support_classes()) {
    UpdateClassBytecode(jni, &live_edit_stubs, support_class.class_name(),
                        support_class.class_data(), /* isProxyClass */ true);
    PrimeClass(jvmti, jni, support_class.class_name());
  }

  live_edit_stubs.CallStaticVoidMethod(
      "addLiveEditedMethod",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
      jni->NewStringUTF(target_class.class_name().c_str()),
      jni->NewStringUTF(target_class.method_name().c_str()),
      jni->NewStringUTF(target_class.method_desc().c_str()));

  Recompose recompose(jvmti, jni);
  jobject reloader = recompose.GetComposeHotReload();
  if (reloader) {
    // This is a temp solution. If the new compose flag is set, we would
    // use the new recompose API. Otherwise we just recompose everything.

    // When the recompose API is stable, we will only call the new API
    // and never call whole program recompose.
    if (req.composable()) {
      std::string error = "";
      bool result = recompose.InvalidateGroupsWithKey(
          reloader, jni->NewStringUTF(target_class.class_name().c_str()),
          req.start_offset(), req.end_offset(), error);
      Log::V("InvalidateGroupsWithKey %d, %d", req.start_offset(),
             req.end_offset());
      if (!result) {
        Log::E("%s", error.c_str());
        resp.set_status(proto::LiveEditResponse::ERROR);
        return resp;
      }
    } else {
      jobject state = recompose.SaveStateAndDispose(reloader);
      recompose.LoadStateAndCompose(reloader, state);
    }
  }

  resp.set_status(proto::LiveEditResponse::OK);
  return resp;
}

}  // namespace deploy
