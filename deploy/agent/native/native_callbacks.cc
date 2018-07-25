#include "native_callbacks.h"

#include "capabilities.h"
#include "hotswap.h"
#include "jni/jni_class.h"
#include "jni/jni_util.h"
#include "proto/config.pb.h"

using swapper::proto::Config;

namespace swapper {
extern const Config* config;

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

bool Native_TryRedefineClasses(JNIEnv* jni, jobject object) {
  JavaVM* vm;
  if (jni->GetJavaVM(&vm) != 0) {
    return false;
  }

  jvmtiEnv* jvmti;
  if (!GetJvmti(vm, jvmti)) {
    return false;
  }

  HotSwap code_swap(jvmti, jni);

  // TODO(acleung): I don't know why we need to redo this but I was getting
  // JVMTI_ERROR_MUST_POSSESS_CAPABILITY without it.
  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    return false;
  }

  bool success = code_swap.DoHotSwap(swapper::config);
  delete swapper::config;
  jvmti->RelinquishCapabilities(&REQUIRED_CAPABILITIES);
  return success;
}

}  // namespace swapper
