#ifndef JNI_UTIL_H
#define JNI_UTIL_H

#include "jni.h"
#include "jvmti.h"

#include <assert.h>
#include <string>

#define NO_DEFAULT_SPECIALIZATION(T) static_assert(sizeof(T) == 0, "");

namespace swapper {

struct JniSignature {
  const char* name;
  const char* signature;
};

// Gets the slot in a method's local variable table that a particularly named
// variable will occupy.
jint GetLocalVariableSlot(jvmtiEnv* jvmti, const jmethodID& method,
                          const char* name) {
  jint entry_count;
  jvmtiLocalVariableEntry* table;
  jvmti->GetLocalVariableTable(method, &entry_count, &table);

  jint slot = -1;
  for (int i = 0; i < (int)entry_count; ++i) {
    jvmtiLocalVariableEntry entry = table[i];

    // We can't return early because we need to deallocate the data from each
    // entry in the table.
    if (std::string(entry.name) == name) {
      assert(slot == -1);
      slot = entry.slot;
    }

    // The local variable table call allocates these.
    jvmti->Deallocate((unsigned char*)entry.name);
    jvmti->Deallocate((unsigned char*)entry.signature);
    jvmti->Deallocate((unsigned char*)entry.generic_signature);
  }

  jvmti->Deallocate((unsigned char*)table);

  return slot;
}

// Gets the name of a class as an std string.
std::string GetClassName(JNIEnv* jni_env, const jclass& klass) {
  // Method IDs can be kept, so we only need to do this once.
  static jclass clazz = jni_env->FindClass("java/lang/Class");
  static jmethodID getName =
      jni_env->GetMethodID(clazz, "getName", "()Ljava/lang/String;");

  jstring name = (jstring)jni_env->CallObjectMethod(klass, getName);

  const char* nativeString = jni_env->GetStringUTFChars(name, 0);
  std::string copy(nativeString);

  jni_env->ReleaseStringUTFChars(name, nativeString);
  jni_env->DeleteLocalRef(name);

  return copy;
}

}  // namespace swapper

#endif