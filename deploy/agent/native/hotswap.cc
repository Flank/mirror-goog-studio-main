/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "tools/base/deploy/agent/native/hotswap.h"

#include <dirent.h>
#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>

#include <algorithm>
#include <fstream>
#include <iostream>

#include "tools/base/deploy/agent/native/dex_verify.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_object.h"
#include "tools/base/deploy/agent/native/recompose.h"
#include "tools/base/deploy/agent/native/thread_suspend.h"
#include "tools/base/deploy/agent/native/variable_reinit.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

jvmtiExtensionFunction GetExtensionFunctionVoid(JNIEnv* env, jvmtiEnv* jvmti,
                                                const std::string& name) {
  jint n_ext = 0;
  jvmtiExtensionFunction res = nullptr;
  jvmtiExtensionFunctionInfo* infos = nullptr;
  if (jvmti->GetExtensionFunctions(&n_ext, &infos) != JVMTI_ERROR_NONE) {
    return res;
  }

  for (jint i = 0; i < n_ext; i++) {
    const jvmtiExtensionFunctionInfo& info = infos[i];
    if (name == info.id) {
      res = info.func;
    }
    for (auto j = 0; j < info.param_count; j++) {
      jvmti->Deallocate(reinterpret_cast<unsigned char*>(info.params[j].name));
    }
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(info.short_description));
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(info.errors));
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(info.id));
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(info.params));
  }
  jvmti->Deallocate(reinterpret_cast<unsigned char*>(infos));
  return res;
}

SwapResult HotSwap::DoHotSwap(const proto::SwapRequest& swap_request) const {
  Phase p("doHotSwap");

  SwapResult result;
  Recompose recompose(jvmti_, jni_);

  // We only try to see if we need HotReload for Apply Code Changes. Otherwise
  // activity restart would re-compose anyways.
  jobject reloader = swap_request.restart_activity()
                         ? nullptr
                         : recompose.GetComposeHotReload();
  jobject state = nullptr;
  if (reloader != nullptr) {
    state = recompose.SaveStateAndDispose(reloader);
  }

  // Define new classes before redefining existing classes.
  if (swap_request.new_classes_size() != 0) {
    DefineNewClasses(swap_request);
  }

  size_t num_modified_classes = swap_request.modified_classes_size();
  jvmtiClassDefinition* def = new jvmtiClassDefinition[num_modified_classes];

  // Build a list of classes that we might need to check for detailed errors.
  const std::string& r_class_prefix = "/R$";
  std::vector<ClassInfo> detailed_error_classes;

  jvmtiExtensionFunction extension = nullptr;
  if (swap_request.structural_redefinition()) {
    extension =
        GetExtensionFunctionVoid(jni_, jvmti_, STRUCTRUAL_REDEFINE_EXTENSION);
  }

  VariableReinitializer var_reinit(swap_request.variable_reinitialization(),
                                   jvmti_, jni_);

  for (size_t i = 0; i < num_modified_classes; i++) {
    const proto::ClassDef& class_def = swap_request.modified_classes(i);
    const std::string code = class_def.dex();

    // ART would do this for us, but we should do it here for consistency's sake
    // and to avoid the logged warning. JVMTI requires class names with slashes.
    std::string name = class_def.name();
    std::replace(name.begin(), name.end(), '.', '/');

    def[i].klass = class_finder_.FindClass(name);

    if (def[i].klass == nullptr) {
      result.status = SwapResult::CLASS_NOT_FOUND;
      result.error_details = class_def.name();
      return result;
    }

    std::string error_msg = "no error";
    SwapResult::Status variableCheck =
        var_reinit.GatherPreviousState(def[i].klass, class_def, &error_msg);
    if (variableCheck != SwapResult::SUCCESS) {
      result.status = variableCheck;
      result.error_details = error_msg;
      return result;
    }

    unsigned char* dex = new unsigned char[code.length()];
    memcpy(dex, code.c_str(), code.length());
    def[i].class_byte_count = code.length();
    def[i].class_bytes = dex;

    // Only run verification on R classes right now.
    if (extension == nullptr &&
        name.find(r_class_prefix) != std::string::npos) {
      ClassInfo info{name, def[i].class_bytes, code.length(), def[i].klass};
      detailed_error_classes.emplace_back(info);
    }
  }

  jvmtiError error_num = JVMTI_ERROR_NONE;

  if (extension == nullptr) {
    error_num = jvmti_->RedefineClasses(num_modified_classes, def);
  } else {
    Log::I("Using Structure Redefinition Extension");

    // When using SRE, we need to stop the world since many operations must be
    // run in an atomic fashion. e.g: Adding a static variable to a class
    // involves not only redefining a class but also initializing the variable"
    ThreadSuspend suspend(jvmti_, jni_);
    std::string suspend_error = "";
    suspend_error = suspend.SuspendUserThreads();

    // TODO: There might be cases where this might be ok.
    //   ie: Debugger suspended threads, user suspended thread..etc.

    // TODO: More importantly, if we do bail, we should also makes sure we
    // resume what we suspended here.
    if (!suspend_error.empty()) {
      // TODO: Logging for now. Metrics might be nice later.
      Log::E("%s", suspend_error.c_str());
    }

    error_num = (*extension)(jvmti_, num_modified_classes, def);

    std::string error_msg = "no error";
    if (error_num == JVMTI_ERROR_NONE) {
      SwapResult::Status variableCheck =
          var_reinit.ReinitializeVariables(&error_msg);
      if (variableCheck != SwapResult::SUCCESS) {
        result.status = variableCheck;
        result.error_details = error_msg;
        return result;
      }
    }

    suspend_error = suspend.ResumeSuspendedThreads();
    if (!suspend_error.empty()) {
      // TODO: Logging for now. Metrics might be nice later.
      Log::E("%s", suspend_error.c_str());
    }
  }

  if (error_num == JVMTI_ERROR_NONE) {
    // If there was no error, we're done.
    result.status = SwapResult::SUCCESS;
  } else {
    // If we failed, try to get some detailed information.
    CheckForClassErrors(jvmti_, detailed_error_classes,
                        &result.jvmti_error_details);

    result.status = SwapResult::JVMTI_ERROR;

    // Get the error associated with the error code from JVMTI.
    char* error = nullptr;
    jvmti_->GetErrorName(error_num, &error);
    result.error_details = error == nullptr ? "" : error;
    jvmti_->Deallocate((unsigned char*)error);
  }

  for (size_t i = 0; i < num_modified_classes; i++) {
    delete[] def[i].class_bytes;
  }

  delete[] def;

  if (reloader != nullptr) {
    recompose.LoadStateAndCompose(reloader, state);
  }

  return result;
}

void HotSwap::DefineNewClasses(const proto::SwapRequest& swap_request) const {
  JniObject thread_loader(jni_, class_finder_.GetApplicationClassLoader());

  jobjectArray dex_bytes_array = jni_->NewObjectArray(
      swap_request.new_classes_size(), jni_->FindClass("[B"), nullptr);

  for (size_t idx = 0; idx < swap_request.new_classes_size(); ++idx) {
    const std::string& dex_file = swap_request.new_classes(idx).dex();
    jbyteArray dex_bytes = jni_->NewByteArray(dex_file.size());
    jni_->SetByteArrayRegion(dex_bytes, 0, dex_file.size(),
                             (const jbyte*)dex_file.c_str());
    jni_->SetObjectArrayElement(dex_bytes_array, idx, dex_bytes);
    jni_->DeleteLocalRef(dex_bytes);
  }

  jobject dex_elements =
      thread_loader.GetJniObjectField("pathList", "Ldalvik/system/DexPathList;")
          .GetObjectField("dexElements",
                          "[Ldalvik/system/"
                          "DexPathList$Element;");

  jobject new_dex_elements =
      JniClass(jni_, "com/android/tools/deploy/instrument/DexUtility")
          .CallStaticObjectMethod("createNewDexElements",
                                  "([[B[Ljava/lang/Object;)[Ljava/lang/Object;",
                                  dex_bytes_array, dex_elements);

  jni_->DeleteLocalRef(dex_bytes_array);

  thread_loader.GetJniObjectField("pathList", "Ldalvik/system/DexPathList;")
      .SetField("dexElements", "[Ldalvik/system/DexPathList$Element;",
                new_dex_elements);

  jni_->DeleteLocalRef(new_dex_elements);
}

}  // namespace deploy
