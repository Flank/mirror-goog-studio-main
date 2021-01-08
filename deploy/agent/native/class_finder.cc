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

#include "tools/base/deploy/agent/native/class_finder.h"

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

jobject ClassFinder::GetThreadClassLoader() const {
  JniClass thread(jni_, "java/lang/Thread");
  return thread
      .CallStaticJniObjectMethod("currentThread", "()Ljava/lang/Thread;")
      .CallObjectMethod("getContextClassLoader", "()Ljava/lang/ClassLoader;");
}

jobject ClassFinder::GetApplicationClassLoader() const {
  JniClass activity_thread(jni_, "android/app/ActivityThread");
  return activity_thread
      .CallStaticJniObjectMethod("currentApplication",
                                 "()Landroid/app/Application;")
      .GetJniObjectField("mLoadedApk", "Landroid/app/LoadedApk;")
      .CallObjectMethod("getClassLoader", "()Ljava/lang/ClassLoader;");
}

jclass ClassFinder::FindInClassLoader(jobject class_loader,
                                      const std::string& name) const {
  if (class_loader == nullptr) {
    Log::E("Class loader was null.");
    return nullptr;
  }

  jstring java_name = jni_->NewStringUTF(name.c_str());
  jclass klass = static_cast<jclass>(
      JniObject(jni_, class_loader)
          .CallObjectMethod(
              "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", java_name));
  jni_->DeleteLocalRef(java_name);
  return klass;
}

jclass ClassFinder::FindInLoadedClasses(const std::string& name) const {
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

jclass ClassFinder::FindClass(const std::string& name) const {
  Log::V("Searching for class '%s' in the thread context classloader.",
         name.c_str());

  jclass klass = FindInClassLoader(GetThreadClassLoader(), name);
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in the application classloader.",
         name.c_str());

  klass = FindInClassLoader(GetApplicationClassLoader(), name);
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

}  // namespace deploy
