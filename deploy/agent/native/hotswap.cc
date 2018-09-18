#include <dirent.h>
#include <stdio.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <iostream>

#include "hotswap.h"
#include "jni.h"
#include "jni/jni_class.h"
#include "jni/jni_object.h"
#include "jvmti.h"
#include "utils/log.h"

namespace swapper {

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
  // ART would do this for us, but we should do it here for consistency's sake
  // and to avoid the logged warning. JVMTI requires class names with slashes.
  std::string fixed_name(name);
  std::replace(fixed_name.begin(), fixed_name.end(), '.', '/');

  Log::V("Searching for class '%s' in the current thread context classloader.",
         fixed_name.c_str());

  jclass klass = FindInClassLoader(GetThreadClassLoader(jni_), fixed_name);
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in the system classloader.",
         fixed_name.c_str());

  klass = jni_->FindClass(fixed_name.c_str());
  if (klass != nullptr) {
    return klass;
  }

  jni_->ExceptionDescribe();
  jni_->ExceptionClear();

  Log::V("Searching for class '%s' in all loaded classes.", fixed_name.c_str());

  klass = FindInLoadedClasses(fixed_name);
  return klass;
}

bool HotSwap::DoHotSwap(const proto::SwapRequest& swap_request,
                        std::string* error_msg) const {
  size_t total_classes = swap_request.classes_size();
  jvmtiClassDefinition* def = new jvmtiClassDefinition[total_classes];

  for (size_t i = 0; i < total_classes; i++) {
    const proto::ClassDef& class_def = swap_request.classes(i);
    const string name = class_def.name();
    const string code = class_def.dex();

    def[i].klass = FindClass(name);
    if (def[i].klass == nullptr) {
      *error_msg = "Could not find class '" + name + "'";
      return false;
    }

    char* dex = new char[code.length()];
    memcpy(dex, code.c_str(), code.length());
    def[i].class_byte_count = code.length();
    def[i].class_bytes = (unsigned char*)dex;
  }

  jvmtiError error_num = jvmti_->RedefineClasses(total_classes, def);

  for (size_t i = 0; i < total_classes; i++) {
    delete[] def[i].class_bytes;
  }

  delete[] def;

  // If there was no error, we're done.
  if (error_num == JVMTI_ERROR_NONE) {
    return true;
  }

  // Otherwise, get the error associated with the error code from JVMTI.
  char* error = nullptr;
  jvmti_->GetErrorName(error_num, &error);
  *error_msg = error == nullptr ? "Unknown" : std::string(error);
  jvmti_->Deallocate((unsigned char*)error);
  return false;
}

}  // namespace swapper
