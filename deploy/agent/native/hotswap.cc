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

jobject GetApplicationClassLoader(JNIEnv* env) {
  JniClass activity_thread(env, "android/app/ActivityThread");
  return activity_thread
      .CallStaticMethod<JniObject>(
          {"currentApplication", "()Landroid/app/Application;"})
      .GetField<JniObject>({"mLoadedApk", "Landroid/app/LoadedApk;"})
      .CallMethod<jobject>({"getClassLoader", "()Ljava/lang/ClassLoader;"});
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
  Log::V("Searching for class '%s' in the thread context classloader.",
         name.c_str());

  jclass klass = FindInClassLoader(GetThreadClassLoader(jni_), name);
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in the application classloader.",
         name.c_str());

  klass = FindInClassLoader(GetApplicationClassLoader(jni_), name);
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

  // Note that this search is for all *loaded* classes; it will not find a class
  // that has not yet been loaded by the VM. Classes are typically loaded when
  // they are first used by the application.
  Log::V("Searching for class '%s' in all loaded classes.", name.c_str());

  klass = FindInLoadedClasses(name);
  return klass;
}

SwapResult HotSwap::DoHotSwap(const proto::SwapRequest& swap_request) const {
  Phase p("doHotSwap");

  SwapResult result;

  // Define new classes before redefining existing classes.
  if (swap_request.new_classes_size() != 0) {
    DefineNewClasses(swap_request);
  }

  size_t modified_classes = swap_request.modified_classes_size();
  jvmtiClassDefinition* def = new jvmtiClassDefinition[modified_classes];

  // Build a list of classes that we might need to check for detailed errors.
  const std::string& r_class_prefix = "/R$";
  std::vector<ClassInfo> detailed_error_classes;

  for (size_t i = 0; i < modified_classes; i++) {
    const proto::ClassDef& class_def = swap_request.modified_classes(i);
    const std::string code = class_def.dex();

    // ART would do this for us, but we should do it here for consistency's sake
    // and to avoid the logged warning. JVMTI requires class names with slashes.
    std::string name = class_def.name();
    std::replace(name.begin(), name.end(), '.', '/');

    def[i].klass = FindClass(name);
    if (def[i].klass == nullptr) {
      result.status = SwapResult::CLASS_NOT_FOUND;
      result.error_details = class_def.name();
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
  jvmtiError error_num = jvmti_->RedefineClasses(modified_classes, def);
  jvmti_->SetVerboseFlag(JVMTI_VERBOSE_OTHER, false);

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

  for (size_t i = 0; i < modified_classes; i++) {
    delete[] def[i].class_bytes;
  }

  delete[] def;

  return result;
}

void HotSwap::DefineNewClasses(const proto::SwapRequest& swap_request) const {
  JniObject thread_loader(jni_, GetApplicationClassLoader(jni_));

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
      thread_loader
          .GetField<JniObject>({"pathList", "Ldalvik/system/DexPathList;"})
          .GetField<jobject>({"dexElements",
                              "[Ldalvik/system/"
                              "DexPathList$Element;"});
  jvalue loader_args[2];
  loader_args[0].l = dex_bytes_array;
  loader_args[1].l = dex_elements;

  jobject new_dex_elements =
      JniClass(jni_, "com/android/tools/deploy/instrument/DexUtility")
          .CallStaticMethod<jobject>(
              {"createNewDexElements",
               "([[B[Ljava/lang/Object;)[Ljava/lang/Object;"},
              loader_args);

  jni_->DeleteLocalRef(dex_bytes_array);

  thread_loader.GetField<JniObject>({"pathList", "Ldalvik/system/DexPathList;"})
      .SetField({"dexElements",
                 "[Ldalvik/system/"
                 "DexPathList$Element;"},
                new_dex_elements);

  jni_->DeleteLocalRef(new_dex_elements);
}

}  // namespace deploy
