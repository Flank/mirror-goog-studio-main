#ifndef NATIVE_CALLBACKS_H
#define NATIVE_CALLBACKS_H

#include "jni.h"
#include "jvmti.h"

#include <vector>

using std::vector;

namespace deploy {

struct NativeBinding {
  const char* class_name;
  JNINativeMethod native_method;

  NativeBinding(const char* name, const char* method_name,
                const char* method_signature, void* native_ptr)
      : class_name(const_cast<char*>(name)) {
    native_method.name = const_cast<char*>(method_name);
    native_method.signature = const_cast<char*>(method_signature);
    native_method.fnPtr = native_ptr;
  }
};

bool RegisterNatives(JNIEnv* jni, const vector<NativeBinding>& bindings);

int Native_GetAppInfoChanged(JNIEnv* jni, jobject object);
bool Native_TryRedefineClasses(JNIEnv* jni, jobject object, jlong request_ptr,
                               jlong socket_ptr);

}  // namespace deploy

#endif
