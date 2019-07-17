#include "tools/base/deploy/installer/tests/fake_jvmti.h"

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

  functions = &functions_;
}

jvmtiError FakeJvmtiEnv::AddCapabilities(
    jvmtiEnv* env, const jvmtiCapabilities* capabilities_ptr) {
  Log::I("JVMTI::AddCapabilities");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::SetEventCallbacks(jvmtiEnv* env,
                                           const jvmtiEventCallbacks* callbacks,
                                           jint size_of_callbacks) {
  Log::I("JVMTI::SetEventCallbacks");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::AddToBootstrapClassLoaderSearch(jvmtiEnv* env,
                                                         const char* segment) {
  Log::I("JVMTI::AddToBootstrapClassLoaderSearch");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::DisposeEnvironment(jvmtiEnv* env) {
  Log::I("JVMTI::DisposeEnvironment");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::Deallocate(jvmtiEnv* env, unsigned char* mem) {
  Log::I("JVMTI::Deallocate");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::GetClassSignature(jvmtiEnv* env, jclass klass,
                                           char** signature_ptr,
                                           char** generic_ptr) {
  Log::I("JVMTI::GetClassSignature");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::GetErrorName(jvmtiEnv* env, jvmtiError error,
                                      char** name_ptr) {
  Log::I("JVMTI::GetErrorName");
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
  Log::I("JVMTI::RedefineClasses");
  return JVMTI_ERROR_NONE;
}

jvmtiError FakeJvmtiEnv::SetVerboseFlag(jvmtiEnv* env, jvmtiVerboseFlag flag,
                                        jboolean value) {
  Log::I("JVMTI::SetVerboseFlag");
  return JVMTI_ERROR_NONE;
}

}  // namespace deploy