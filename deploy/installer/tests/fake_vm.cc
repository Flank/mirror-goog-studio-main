#include "tools/base/deploy/installer/tests/fake_vm.h"

namespace deploy {

FakeJavaVm::FakeJavaVm()
    : gJniInvokeInterface{
          nullptr,  // reserved0
          nullptr,  // reserved1
          nullptr,  // reserved2
          DestroyJavaVM, AttachCurrentThread,        DetachCurrentThread,
          GetEnv,        AttachCurrentThreadAsDaemon} {
  functions = &gJniInvokeInterface;
}

jint FakeJavaVm::DestroyJavaVM(JavaVM* vm) { return JNI_OK; }

jint FakeJavaVm::AttachCurrentThread(JavaVM* vm, void** penv, void* args) {
  return JNI_OK;
}

jint FakeJavaVm::DetachCurrentThread(JavaVM* vm) { return JNI_OK; }

jint FakeJavaVm::GetEnv(JavaVM* vm, void** penv, jint version) {
  if (version == JVMTI_VERSION_1_2) {
    *penv = &((FakeJavaVm*)vm)->jvmti_env_;
    return JNI_OK;
  }
  if (version == JNI_VERSION_1_2) {
    *penv = &((FakeJavaVm*)vm)->jni_env_;
    return JNI_OK;
  }
  return JNI_ERR;
}

jint FakeJavaVm::AttachCurrentThreadAsDaemon(JavaVM* vm, void** penv,
                                             void* args) {
  return JNI_OK;
}

}  // namespace deploy
