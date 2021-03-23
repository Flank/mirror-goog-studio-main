#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fstream>
#include <sstream>

#include "jvmti/jvmti_helper.h"
#include "utils/log.h"

using namespace profiler;

/**
 * This agent works as follow:
 * 1. Take app package name as argument.
 * 2. Register a ClassFileLoadHook.
 * 3. Monitor to find kotlin/coroutines/jvm/internal/DebugProbesKt.
 * 4. On found,
 *    4.1. Read dex file containing new bytecode for DebugProbesKt.
 *    4.2. Replace DebugProbesKt class data.
 *    4.3  Unregister ClassFileLoadHook and register ClassPrepare.
 * 5. Monitor to find Lkotlinx/coroutines/debug/internal/DebugProbesImpl.
 * 6. On found, call DebugProbesImpl#Install
 */

namespace {
std::string package_name;
}

// TODO(b/182023904): remove all calls to LOG:D
int ReadFile(jvmtiEnv* jvmti, const std::string& file_name, int* file_size,
             unsigned char** buffer) {
  int file_descriptor = open(file_name.c_str(), O_RDONLY);
  if (file_descriptor == -1) {
    return -1;
  }

  struct stat statbuf;
  int stat_res = stat(file_name.c_str(), &statbuf);
  if (stat_res == -1) {
    return -1;
  }

  *file_size = statbuf.st_size;
  jvmti->Allocate(*file_size, buffer);
  int read_res = read(file_descriptor, *buffer, *file_size);
  if (read_res == -1) {
    return -1;
  }

  int close_res = close(file_descriptor);
  if (close_res == -1) {
    return -1;
  }

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

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Transforming %s", name);

  std::string file_name = "/data/data/";
  file_name += package_name;
  file_name += "/code_cache/classes.dex";

  int file_size;
  unsigned char* buffer;

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Reading file : %s", file_name.c_str());

  // read file
  int res = ReadFile(jvmti, file_name, &file_size, &buffer);
  if (res == -1) {
    Log::E(Log::Tag::COROUTINE_DEBUGGER, "Failed to read file: %s",
           file_name.c_str());
    SetEventNotification(jvmti, JVMTI_DISABLE,
                         JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
    return;
  }

  *new_class_data_len = file_size;
  *new_class_data = buffer;

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Successfully transformed %s", name);

  // enable events notifications to call DebugProbesImpl#install in
  // ClassPrepare. We should call DebugProbesImpl#install only if
  // DebugProbesKt is transformed
  SetEventNotification(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE);

  // DebugProbesKt is the only class we need to transform, so we can disable
  // events
  SetEventNotification(jvmti, JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
}

static void JNICALL ClassPrepare(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread,
                                 jclass klass) {
  char* className;
  jvmti->GetClassSignature(klass, &className, NULL);
  const std::string class_name(className);
  jvmti->Deallocate((unsigned char*)className);

  if (class_name != "Lkotlinx/coroutines/debug/internal/DebugProbesImpl;") {
    return;
  }

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Calling DebugProbesImpl#install");

  // create object by calling constructor
  jmethodID constructor = jni->GetMethodID(klass, "<init>", "()V");
  jobject debug_probes_impl_obj = jni->NewObject(klass, constructor);

  // invoke install method
  jmethodID install = jni->GetMethodID(klass, "install", "()V");
  jni->CallVoidMethod(debug_probes_impl_obj, install);

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "DebugProbesImpl#install called.");

  // disable event notifications for ClassPrepare
  SetEventNotification(jvmti, JVMTI_DISABLE, JVMTI_EVENT_CLASS_PREPARE);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  package_name.assign(options);

  // This will attach the current thread to the vm, otherwise CreateJvmtiEnv(vm)
  // below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);

  Log::D(Log::Tag::COROUTINE_DEBUGGER, "Options: %s", options);

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
  callbacks.ClassPrepare = &ClassPrepare;

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
