#include "tools/base/debug/coroutine/native/agent/DebugProbesKt.h"
#include "tools/base/transport/native/jvmti/jvmti_helper.h"
#include "tools/base/transport/native/utils/log.h"

using namespace profiler;

/**
 * This agent works as follow:
 * 1. Register a ClassFileLoadHook.
 * 2. Monitor to find kotlin/coroutines/jvm/internal/DebugProbesKt.
 * 3. On found,
 *    3.1 Check that DebugProbesImpl is loaded or loadable,
 *        and it's not an old version.
 *    3.2 Call `install` on DebugProbesImpl.
 *    3.3 Replace DebugProbesKt class data with DebugProbesKt
 *        from DebugProbesKt.h. DebugProbesKt.h is create
 *        by a genrule, using resource/DebugProbesKt.bindex.
 *        resource/DebugProbesKt.bindex is a dexed version
 *        of DebugProbesKt.bin from
 *        kotlinx-coroutines-core.
 *    3.4 Unregister ClassFileLoadHook.
 */

// TODO(b/182023904): remove all calls to LOG:D

// check if DebugProbesImpl exists and that it's new version, then call
// DebugProbesImpl#install
int installDebugProbes(JNIEnv* jni) {
  jclass klass =
      jni->FindClass("kotlinx/coroutines/debug/internal/DebugProbesImpl");
  if (klass == NULL) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl not found");
    return -1;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl found");

  // check that it's the correct version
  // only older versions of this class have the method
  // `startWeakRefCleanerThread`
  jmethodID startWeakRefCleanerThread =
      jni->GetMethodID(klass, "startWeakRefCleanerThread", "()V");
  if (startWeakRefCleanerThread == NULL) {
    Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl is old");
    return -1;
  }

  // create DebugProbesImpl object by calling constructor
  jmethodID constructor = jni->GetMethodID(klass, "<init>", "()V");
  jobject debug_probes_impl_obj = jni->NewObject(klass, constructor);

  // invoke install method
  jmethodID install = jni->GetMethodID(klass, "install", "()V");
  jni->CallVoidMethod(debug_probes_impl_obj, install);

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl#install called.");
  return 0;
}

static void JNICALL
ClassFileLoadHook(jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined,
                  jobject loader, const char* name, jobject protection_domain,
                  jint class_data_len, const unsigned char* class_data,
                  jint* new_class_data_len, unsigned char** new_class_data) {
  // transform DebugProbesKt
  std::string class_name(name);
  if (class_name != "kotlin/coroutines/jvm/internal/DebugProbesKt") {
    return;
  }

  int installed = installDebugProbes(jni);
  if (installed < 0) {
    SetEventNotification(jvmti, JVMTI_DISABLE,
                         JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
    return;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Transforming %s", name);

  *new_class_data_len = kDebugProbesKt_len;
  *new_class_data = kDebugProbesKt;

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Successfully transformed %s", name);

  // DebugProbesKt is the only class we need to transform, so we can disable
  // events
  SetEventNotification(jvmti, JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  // This will attach the current thread to the vm, otherwise CreateJvmtiEnv(vm)
  // below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);

  jvmtiEnv* jvmti = CreateJvmtiEnv(vm);
  if (jvmti == nullptr) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER, "Failed to initialize JVMTI env.");
    return -1;
  }

  // set JVMTI capabilities
  jvmtiCapabilities capa;
  bool hasError =
      CheckJvmtiError(jvmti, jvmti->GetPotentialCapabilities(&capa));
  if (hasError) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER,
           "JVMTI GetPotentialCapabilities error.");
    return -1;
  }
  SetAllCapabilities(jvmti);
  Log::D(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetAllCapabilities done.");

  // set JVMTI callbacks
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = &ClassFileLoadHook;

  hasError = CheckJvmtiError(
      jvmti, jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks)));
  if (hasError) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetEventCallbacks error");
    return -1;
  }
  Log::D(Log::Tag::COROUTINE_DEBUGGER, "JVMTI SetEventCallbacks done.");

  // enable events notification
  // TODO(b/182023904): see b/152421535, make sure that this doesn't crash on
  // pre API 29
  SetEventNotification(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);

  return JNI_OK;
}
