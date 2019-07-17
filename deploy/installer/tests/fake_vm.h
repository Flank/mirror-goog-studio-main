#ifndef DEPLOY_INSTALLER_TESTS_FAKE_VM_H
#define DEPLOY_INSTALLER_TESTS_FAKE_VM_H

#include "tools/base/deploy/installer/tests/fake_jni.h"
#include "tools/base/deploy/installer/tests/fake_jvmti.h"

namespace deploy {

struct FakeJavaVm : public JavaVM {
  FakeJavaVm();

  static jint DestroyJavaVM(JavaVM* vm);
  static jint AttachCurrentThread(JavaVM* vm, void** penv, void* args);
  static jint DetachCurrentThread(JavaVM* vm);
  static jint GetEnv(JavaVM* vm, void** penv, jint version);
  static jint AttachCurrentThreadAsDaemon(JavaVM* vm, void** penv, void* args);

 private:
  FakeJvmtiEnv jvmti_env_;
  FakeJNIEnv jni_env_;

  const JNIInvokeInterface_ gJniInvokeInterface;
};

}  // namespace deploy

#endif  // n