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

#include <algorithm>
#include <fstream>
#include <iostream>

#include <dirent.h>
#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>

#include "tools/base/deploy/agent/native/dex_verify.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_object.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

jobject GetThreadClassLoader(JNIEnv* env) {
  JniClass thread(env, "java/lang/Thread");
  return thread
      .CallStaticMethod<JniObject>({"currentThread", "()Ljava/lang/Thread;"})
      .CallMethod<jobject>(
          {"getContextClassLoader", "()Ljava/lang/ClassLoader;"});
}

jclass HotSwap::FindInClassLoader(jobject class_loader,
                                  const std::string& name) const {
  if (class_loader == nullptr) {
    Log::E("Class loader was null.");
    return nullptr;
  }

  jvalue java_name = {.l = jni_->NewStringUTF(name.c_str())};
  jclass klass = static_cast<jclass>(
      JniObject(jni_, class_loader)
          .CallMethod<jobject>(
              {"findClass", "(Ljava/lang/String;)Ljava/lang/Class;"},
              &java_name));
  jni_->DeleteLocalRef(java_name.l);
  return klass;
}

jclass HotSwap::FindInLoadedClasses(const std::string& name) const {
  jint class_count;
  jclass* classes;
  if (jvmti_->GetLoadedClasses(&class_count, &classes) != JVMTI_ERROR_NONE) {
    Log::E("Could not enumerate loaded classes.");
    return nullptr;
  }

  // Put the class name in the proper format.
  std::string search_signature = "L" + name + ";";

  jclass klass = nullptr;
  for (int i = 0; i < class_count; ++i) {
    char* signature_ptr;
    jvmti_->GetClassSignature(classes[i], &signature_ptr,
                              /* generic_ptr */ nullptr);

    // Can't return early because we need to finish freeing the local
    // references, so we use the time to check for erroneous duplicates.
    if (search_signature != signature_ptr) {
      jni_->DeleteLocalRef(classes[i]);
    } else if (klass == nullptr) {
      klass = classes[i];
    } else {
      jni_->DeleteLocalRef(classes[i]);
      Log::E(
          "The same class was found multiple times in the loaded classes list: "
          "%s",
          search_signature.c_str());
    }

    jvmti_->Deallocate((unsigned char*)signature_ptr);
  }

  jvmti_->Deallocate((unsigned char*)classes);
  return klass;
}

jclass HotSwap::FindClass(const std::string& name) const {
  Log::V("Searching for class '%s' in the current thread context classloader.",
         name.c_str());

  jclass klass = FindInClassLoader(GetThreadClassLoader(jni_), name);
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in the system classloader.", name.c_str());

  klass = jni_->FindClass(name.c_str());
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in all loaded classes.", name.c_str());

  klass = FindInLoadedClasses(name);
  return klass;
}

SwapResult HotSwap::DoHotSwap(const proto::SwapRequest& swap_request) const {
  Phase p("doHotSwap");

  SwapResult result;

  size_t total_classes = swap_request.classes_size();
  jvmtiClassDefinition* def = new jvmtiClassDefinition[total_classes];

  // Build a list of classes that we might need to check for detailed errors.
  const std::string& r_class_prefix = "/R$";
  std::vector<ClassInfo> detailed_error_classes;

  for (size_t i = 0; i < total_classes; i++) {
    const proto::ClassDef& class_def = swap_request.classes(i);
    const std::string code = class_def.dex();

    // ART would do this for us, but we should do it here for consistency's sake
    // and to avoid the logged warning. JVMTI requires class names with slashes.
    std::string name = class_def.name();
    std::replace(name.begin(), name.end(), '.', '/');

    def[i].klass = FindClass(name);
    if (def[i].klass == nullptr) {
      result.success = false;
      result.error_code = "Could not find class '" + name + "'";
      return result;
    }

    unsigned char* dex = new unsigned char[code.length()];
    memcpy(dex, code.c_str(), code.length());
    def[i].class_byte_count = code.length();
    def[i].class_bytes = dex;

    // Only run verification on R classes right now.
    if (name.find(r_class_prefix) != std::string::npos) {
      ClassInfo info{name, def[i].class_bytes, code.length(), def[i].klass};
      detailed_error_classes.emplace_back(info);
    }
  }

  // We make the JVMTI verifier verbose. If verification fails, at least
  // we can ask the user for logcats.
  jvmti_->SetVerboseFlag(JVMTI_VERBOSE_OTHER,
                         true);  // Best Effort, ignore erros.
  jvmtiError error_num = jvmti_->RedefineClasses(total_classes, def);
  jvmti_->SetVerboseFlag(JVMTI_VERBOSE_OTHER, false);

  if (error_num == JVMTI_ERROR_NONE) {
    // If there was no error, we're done.
    result.success = true;
    result.error_code = "";
  } else {
    // If we failed, try to get some detailed information.
    CheckForClassErrors(jvmti_, detailed_error_classes, &result.error_details);

    // Otherwise, get the error associated with the error code from JVMTI.
    result.success = false;
    char* error = nullptr;
    jvmti_->GetErrorName(error_num, &error);
    result.error_code = error == nullptr ? "Unknown" : std::string(error);
    jvmti_->Deallocate((unsigned char*)error);
  }

  for (size_t i = 0; i < total_classes; i++) {
    delete[] def[i].class_bytes;
  }

  delete[] def;

  return result;
}

}  // namespace deploy
