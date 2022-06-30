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

#include <unordered_map>
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
// Returns true if the class was just primed, false otherwise
bool PrimeClass(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& class_name) {
  if (primed_classes.find(class_name) != primed_classes.end()) {
    return false;
  }

  auto cache = std::make_unique<DisabledTransformCache>();
  Instrumenter instrumenter(jvmti, jni, std::move(cache), false);

  const StubTransform stub(class_name);
  instrumenter.Instrument(stub);
  primed_classes.insert(class_name);

  Log::V("Live Edit primed %s", class_name.c_str());
  return true;
}

jobjectArray UpdateClassBytecode(JNIEnv* jni, JniClass* live_edit_stubs,
                                 const proto::LiveEditRequest& req) {
  auto target_class = req.target_class();
  jbyteArray target_bytes = jni->NewByteArray(target_class.class_data().size());
  jni->SetByteArrayRegion(target_bytes, 0, target_class.class_data().size(),
                          (jbyte*)target_class.class_data().data());

  jobjectArray proxy_arr = jni->NewObjectArray(req.support_classes_size(),
                                               jni->FindClass("[B"), nullptr);
  for (int i = 0; i < req.support_classes_size(); ++i) {
    auto support_class = req.support_classes()[i];
    jbyteArray proxy_bytes =
        jni->NewByteArray(support_class.class_data().size());
    jni->SetByteArrayRegion(proxy_bytes, 0, support_class.class_data().size(),
                            (jbyte*)support_class.class_data().data());
    jni->SetObjectArrayElement(proxy_arr, i, proxy_bytes);
  }

  return (jobjectArray)live_edit_stubs->CallStaticObjectMethod(
      "addClasses",
      "([B[[B)[Lcom/"
      "android/tools/deploy/liveedit/BytecodeValidator$UnsupportedChange;",
      target_bytes, proxy_arr);
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

proto::AgentLiveEditResponse LiveEdit(jvmtiEnv* jvmti, JNIEnv* jni,
                                      const proto::LiveEditRequest& req) {
  proto::AgentLiveEditResponse resp;

  if (SetUpInstrumentationJar(jvmti, jni, req.package_name()).empty()) {
    resp.set_status(proto::AgentLiveEditResponse::INSTRUMENTATION_FAILED);
    return resp;
  }

  auto app_loader = ClassFinder(jvmti, jni).GetApplicationClassLoader();

  // Add the LiveEdit dex library to the application classloader.
  if (!SetUpLiveEditDex(jvmti, jni, req.package_name())) {
    resp.set_status(proto::AgentLiveEditResponse::LAMBDA_DEX_LOAD_FAILED);
    return resp;
  }

  SetDebugMode(jni, req.debugmodeenabled());

  JniClass live_edit_stubs(jni,
                           "com/android/tools/deploy/liveedit/LiveEditStubs");
  live_edit_stubs.CallStaticVoidMethod("init", "(Ljava/lang/ClassLoader;)V",
                                       app_loader);

  jobjectArray errors = UpdateClassBytecode(jni, &live_edit_stubs, req);
  auto err_count = jni->GetArrayLength(errors);

  // Must stay in sync with the enum in BytecodeValidator.UnsupportedChange
  static std::unordered_map<std::string, proto::UnsupportedChange::Type>
      type_map(
          {{"ADDED_METHOD", proto::UnsupportedChange::ADDED_METHOD},
           {"REMOVED_METHOD", proto::UnsupportedChange::REMOVED_METHOD},
           {"ADDED_CLASS", proto::UnsupportedChange::ADDED_CLASS},
           {"ADDED_FIELD", proto::UnsupportedChange::ADDED_FIELD},
           {"REMOVED_FIELD", proto::UnsupportedChange::REMOVED_FIELD},
           {"MODIFIED_FIELD", proto::UnsupportedChange::MODIFIED_FIELD},
           {"MODIFIED_SUPER", proto::UnsupportedChange::MODIFIED_SUPER},
           {"ADDED_INTERFACE", proto::UnsupportedChange::ADDED_INTERFACE},
           {"REMOVED_INTERFACE", proto::UnsupportedChange::REMOVED_INTERFACE}});

  if (err_count > 0) {
    resp.set_status(proto::AgentLiveEditResponse::UNSUPPORTED_CHANGE);
    for (int i = 0; i < err_count; ++i) {
      JniObject error(jni, jni->GetObjectArrayElement(errors, i));
      auto proto = resp.add_errors();
      proto->set_class_name(
          error.GetJniObjectField("className", "Ljava/lang/String;")
              .ToString());
      proto->set_target_name(
          error.GetJniObjectField("targetName", "Ljava/lang/String;")
              .ToString());
      proto->set_file_name(
          error.GetJniObjectField("fileName", "Ljava/lang/String;").ToString());
      proto->set_line_number(error.GetIntField("lineNumber", "I"));

      // The type field in the proto defaults to UNKNOWN if no value is found.
      auto type = type_map.find(
          error.GetJniObjectField("type", "Ljava/lang/String;").ToString());
      if (type != type_map.end()) {
        proto->set_type(type->second);
      }
    }
    return resp;
  }

  const auto& target_class = req.target_class();
  const bool needFullRecompose =
      PrimeClass(jvmti, jni, target_class.class_name());
  for (auto support_class : req.support_classes()) {
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
    if (req.composable() && !needFullRecompose) {
      std::string error = "";
      bool result = recompose.InvalidateGroupsWithKey(
          reloader, jni->NewStringUTF(target_class.class_name().c_str()),
          req.group_id(), error);
      Log::V("InvalidateGroupsWithKey %d", req.group_id());
      if (!result) {
        Log::E("%s", error.c_str());
        resp.set_status(proto::AgentLiveEditResponse::ERROR);
        return resp;
      }
    } else {
      jobject state = recompose.SaveStateAndDispose(reloader);
      recompose.LoadStateAndCompose(reloader, state);
    }
  }

  resp.set_status(proto::AgentLiveEditResponse::OK);
  return resp;
}

}  // namespace deploy
