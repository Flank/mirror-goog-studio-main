#include <dirent.h>
#include <stdio.h>
#include <cstring>
#include <fstream>
#include <iostream>

#include "hotswap.h"
#include "jni.h"
#include "jni/jni_class.h"
#include "jni/jni_object.h"
#include "jvmti.h"
#include "utils/log.h"

using namespace std;

namespace swapper {

jobject GetThreadClassLoader(JNIEnv* env) {
  JniClass thread(env, "java/lang/Thread");
  return thread
      .CallStaticMethod<JniObject>({"currentThread", "()Ljava/lang/Thread;"})
      .CallMethod<jobject>(
          {"getContextClassLoader", "()Ljava/lang/ClassLoader;"});
}

jclass HotSwap::FindClass(const std::string& name) const {
  JniObject gClassLoader(jni_, GetThreadClassLoader(jni_));
  jvalue java_name = {.l = jni_->NewStringUTF(name.c_str())};
  jclass klass = static_cast<jclass>(gClassLoader.CallMethod<jobject>(
      {"findClass", "(Ljava/lang/String;)Ljava/lang/Class;"}, &java_name));
  jni_->DeleteLocalRef(java_name.l);

  if (klass != nullptr) {
    return klass;
  }

  jthrowable e = jni_->ExceptionOccurred();
  jni_->ExceptionClear();
  jclass clazz = jni_->GetObjectClass(e);
  jmethodID get_message =
      jni_->GetMethodID(clazz, "getMessage", "()Ljava/lang/String;");
  jstring message = (jstring)jni_->CallObjectMethod(e, get_message);
  const char* mstr = jni_->GetStringUTFChars(message, nullptr);
  Log::V("Exception calling find class on %s %s\n", name.c_str(), mstr);

  // Fall back to the system classloader.
  return jni_->FindClass(name.c_str());
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

  Log::V("Done HotSwapping!");

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
