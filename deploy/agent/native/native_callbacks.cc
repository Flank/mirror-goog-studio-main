#include "native_callbacks.h"

#include "hotswap.h"
#include "jni/jni_class.h"
#include "jni/jni_util.h"

namespace swapper {

bool RegisterNatives(JNIEnv* jni, const vector<NativeBinding>& bindings) {
  for (auto& binding : bindings) {
    jclass klass = jni->FindClass(binding.class_name);

    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
      return false;
    }

    jni->RegisterNatives(klass, &binding.native_method, 1);
    jni->DeleteLocalRef(klass);
  }

  return true;
}

int Native_GetAppInfoChanged(JNIEnv* jni, jobject object) {
  JniClass activity_thread_h(jni, "android/app/ActivityThread$H");
  return activity_thread_h.GetStaticField<jint>(
      {"APPLICATION_INFO_CHANGED", "I"});
}

bool Native_TryRedefineClasses(JNIEnv* jni, jobject object, jstring dex_dir) {
  JavaVM* vm;
  if (jni->GetJavaVM(&vm) != 0) {
    return false;
  }

  jvmtiEnv* jvmti;
  if (!GetJvmti(vm, jvmti)) {
    return false;
  }

  HotSwap code_swap(jvmti, jni);
  if (!code_swap.DoHotSwap(JStringToString(jni, dex_dir))) {
    // TODO: Log meaningful error.
    return false;
  }

  return true;
}

}  // namespace swapper