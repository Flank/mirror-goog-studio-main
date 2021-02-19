#include "tools/base/deploy/installer/tests/fake_jvmti.h"

#include <string.h>

#include "tools/base/deploy/agent/native/jvmti/android.h"
#include "tools/base/deploy/installer/tests/fake_jni.h"

#define FAKE_JVMTI(NAME, ARGS...)                      \
  jvmtiError FakeJvmtiEnv::NAME(jvmtiEnv* env, ARGS) { \
    Log::I("JVMTI::" #NAME);                           \
    return JVMTI_ERROR_NONE;                           \
  }

namespace deploy {

FakeJvmtiEnv::FakeJvmtiEnv() : functions_{0} {
  functions_.AddCapabilities = &AddCapabilities;
  functions_.SetEventCallbacks = &SetEventCallbacks;
  functions_.DisposeEnvironment = &DisposeEnvironment;
  functions_.Deallocate = &Deallocate;
  functions_.GetClassSignature = &GetClassSignature;
  functions_.GetErrorName = &GetErrorName;
  functions_.GetLoadedClasses = &GetLoadedClasses;
  functions_.RedefineClasses = &RedefineClasses;
  functions_.SetVerboseFlag = &SetVerboseFlag;
  functions_.AddToBootstrapClassLoaderSearch = &AddToBootstrapClassLoaderSearch;
  functions_.GetExtensionFunctions = &GetExtensionFunctions;
  functions_.SetEventNotificationMode = &SetEventNotificationMode;
  functions_.RetransformClasses = &RetransformClasses;
  functions = &functions_;
}

FAKE_JVMTI(AddCapabilities, const jvmtiCapabilities* capabilities_ptr)

FAKE_JVMTI(AddToBootstrapClassLoaderSearch, const char* segment)

// If you decide to implement Deallocate, you should also change
// GetExtensionFunctions to return freeable stuff (not freeable as of now).
FAKE_JVMTI(Deallocate, unsigned char* mem)

jvmtiError FakeJvmtiEnv::DisposeEnvironment(jvmtiEnv* env) {
  Log::I("JVMTI::DisposeEnvironment");
  return JVMTI_ERROR_NONE;
}

FAKE_JVMTI(GetClassSignature, jclass klass, char** signature_ptr,
           char** generic_ptr)

FAKE_JVMTI(GetErrorName, jvmtiError error, char** name_ptr)

namespace FakeJVMTIExtension {
jvmtiError FakeGetHiddenApiEnforcementPolicy(jvmtiEnv* env, jint* policy_) {
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeDisableHiddenApiEnforcementPolicy(jvmtiEnv* env) {
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeSetHiddenApiEnforcementPolicy(jvmtiEnv* env, jint* policy) {
  return JVMTI_ERROR_NONE;
}
};  // namespace FakeJVMTIExtension

namespace {
char* ToChar(const std::string& s) {
  char* cstr = new char[s.length() + 1];
  strcpy(cstr, s.c_str());
  return cstr;
}
}  // namespace

jvmtiError FakeJvmtiEnv::GetExtensionFunctions(
    jvmtiEnv* env, jint* extension_count_ptr,
    jvmtiExtensionFunctionInfo** extensions) {
  static jvmtiExtensionFunctionInfo info[] = {
      {.func = (jvmtiExtensionFunction)
           FakeJVMTIExtension::FakeGetHiddenApiEnforcementPolicy,
       .id = ToChar(android::jvmti::kGetFuncKey),
       .short_description = nullptr,
       .param_count = 0,
       .params = nullptr,
       .error_count = 0,
       .errors = nullptr},
      {.func = (jvmtiExtensionFunction)
           FakeJVMTIExtension::FakeSetHiddenApiEnforcementPolicy,
       .id = ToChar(android::jvmti::kSetFuncKey),
       .short_description = nullptr,
       .param_count = 0,
       .params = nullptr,
       .error_count = 0,
       .errors = nullptr},
      {.func = (jvmtiExtensionFunction)
           FakeJVMTIExtension::FakeDisableHiddenApiEnforcementPolicy,
       .id = ToChar(android::jvmti::kDisFuncKey),
       .short_description = nullptr,
       .param_count = 0,
       .params = nullptr,
       .error_count = 0,
       .errors = nullptr}};
  *extensions = info;
  Log::I("JVMTI::GetExtensionFunctions");
  *extension_count_ptr = sizeof(info) / sizeof(info[0]);
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::GetLoadedClasses(jvmtiEnv* env, jint* class_count_ptr,
                                          jclass** classes_ptr) {
  Log::I("JVMTI::GetLoadedClasses");
  *class_count_ptr = 0;
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::RedefineClasses(
    jvmtiEnv* env, jint class_count,
    const jvmtiClassDefinition* class_definitions) {
  for (int i = 0; i < class_count; ++i) {
    FakeClass* clazz = (FakeClass*)class_definitions[i].klass;
    Log::I("JVMTI::RedefineClasses:%s", clazz->name.c_str());
  }
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::RetransformClasses(jvmtiEnv* jvmtiEnv,
                                            jint class_count,
                                            const jclass* classes) {
  for (int i = 0; i < class_count; ++i) {
    FakeClass* clazz = (FakeClass*)classes[i];
    Log::I("JVMTI::RetransformClasses:%s", clazz->name.c_str());
  }
  return JVMTI_ERROR_NONE;
}

FAKE_JVMTI(SetEventCallbacks, const jvmtiEventCallbacks* callbacks,
           jint size_of_callbacks)

FAKE_JVMTI(SetEventNotificationMode, jvmtiEventMode mode, jvmtiEvent event_type,
           jthread event_thread, ...)

FAKE_JVMTI(SetVerboseFlag, jvmtiVerboseFlag flag, jboolean value)

}  // namespace deploy
