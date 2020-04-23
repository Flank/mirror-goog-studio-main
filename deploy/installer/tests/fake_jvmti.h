/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef DEPLOY_INSTALLER_TESTS_FAKE_JVMTI_H
#define DEPLOY_INSTALLER_TESTS_FAKE_JVMTI_H

#include <jvmti.h>

#include "tools/base/deploy/common/log.h"

using deploy::Log;

namespace deploy {

class FakeJvmtiEnv : public jvmtiEnv {
 public:
  FakeJvmtiEnv();

  static jvmtiError AddCapabilities(jvmtiEnv* env,
                                    const jvmtiCapabilities* capabilities_ptr);

  static jvmtiError SetEventCallbacks(jvmtiEnv* env,
                                      const jvmtiEventCallbacks* callbacks,
                                      jint size_of_callbacks);

  static jvmtiError AddToBootstrapClassLoaderSearch(jvmtiEnv* env,
                                                    const char* segment);

  static jvmtiError DisposeEnvironment(jvmtiEnv* env);

  static jvmtiError Deallocate(jvmtiEnv* env, unsigned char* mem);

  static jvmtiError GetClassSignature(jvmtiEnv* env, jclass klass,
                                      char** signature_ptr, char** generic_ptr);

  static jvmtiError GetErrorName(jvmtiEnv* env, jvmtiError error,
                                 char** name_ptr);

  static jvmtiError GetLoadedClasses(jvmtiEnv* env, jint* class_count_ptr,
                                     jclass** classes_ptr);

  static jvmtiError RedefineClasses(
      jvmtiEnv* env, jint class_count,
      const jvmtiClassDefinition* class_definitions);

  static jvmtiError SetVerboseFlag(jvmtiEnv* env, jvmtiVerboseFlag flag,
                                   jboolean value);

  static jvmtiError GetExtensionFunctions(
      jvmtiEnv* env, jint* extension_count_ptr,
      jvmtiExtensionFunctionInfo** extensions);

  static jvmtiError SetEventNotificationMode(jvmtiEnv* env, jvmtiEventMode mode,
                                             jvmtiEvent event_type,
                                             jthread event_thread, ...);

  static jvmtiError RetransformClasses(jvmtiEnv* jvmtiEnv, jint class_count,
                                       const jclass* classes);

 private:
  jvmtiInterface_1_ functions_;
};

}  // namespace deploy

#endif  // DEPLOY_INSTALLER_TESTS_FAKE_JVMTI_H
