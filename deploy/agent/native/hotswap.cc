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

bool HotSwap::DoHotSwap(const proto::SwapRequest& swap_request) const {
  bool success = true;
  size_t total_classes = swap_request.classes_size();
  jvmtiClassDefinition* def = new jvmtiClassDefinition[total_classes];
  JniObject gClassLoader(jni_, GetThreadClassLoader(jni_));

  for (size_t i = 0; i < total_classes; i++) {
    const proto::ClassDef& classDef = swap_request.classes(i);
    const string name = classDef.name();
    const string code = classDef.dex();
    jvalue java_name = {.l = jni_->NewStringUTF(name.c_str())};
    jclass klass = static_cast<jclass>(gClassLoader.CallMethod<jobject>(
        {"findClass", "(Ljava/lang/String;)Ljava/lang/Class;"}, &java_name));
    jni_->DeleteLocalRef(java_name.l);

    if (!klass) {
      jthrowable e = jni_->ExceptionOccurred();
      jni_->ExceptionClear();
      jclass clazz = jni_->GetObjectClass(e);
      jmethodID getMessage =
          jni_->GetMethodID(clazz, "getMessage", "()Ljava/lang/String;");
      jstring message = (jstring)jni_->CallObjectMethod(e, getMessage);
      const char* mstr = jni_->GetStringUTFChars(message, nullptr);
      Log::E("Exception calling find class on %s %s\n", name.c_str(), mstr);
      jni_->ExceptionClear();
      // Re-try with the normal class loader.
      klass = jni_->FindClass(name.c_str());
      if (!klass) {
        Log::E("Cannot find class %s for redefinition\n", name.c_str());
        return false;
      }
    }
    def[i].klass = klass;
    char* dex = new char[code.length()];
    memcpy(dex, code.c_str(), code.length());
    def[i].class_byte_count = code.length();
    def[i].class_bytes = (unsigned char*)dex;
  }

  jvmtiError err = jvmti_->RedefineClasses(total_classes, def);

  if (err == JVMTI_ERROR_UNMODIFIABLE_CLASS) {
    // TODO(acleung): Most likely an interface. Need to inform the caller later.
    return true;
  }

  for (size_t i = 0; i < total_classes; i++) {
    delete[] def[i].class_bytes;
  }

  delete[] def;
  // TODO(acleung): Inform caller of specific JVMTI Errors.
  Log::V("Done HotSwapping!");
  if (err) {
    Log::E("Error RedefineClasses: %d \n", err);
    return false;
  } else {
    return success;
  }
}
}  // namespace swapper
