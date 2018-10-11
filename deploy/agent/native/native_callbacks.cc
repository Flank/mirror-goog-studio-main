/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "tools/base/deploy/agent/native/native_callbacks.h"

#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_object.h"
#include "tools/base/deploy/agent/native/swapper.h"

namespace deploy {

bool RegisterNative(JNIEnv* jni, const NativeBinding& binding) {
  jclass klass = jni->FindClass(binding.class_name);
  if (jni->ExceptionCheck()) {
    jni->ExceptionClear();
    return false;
  }

  jni->RegisterNatives(klass, &binding.native_method, 1);
  jni->DeleteLocalRef(klass);
  return true;
}

void Native_UpdateApplicationInfo(JNIEnv* jni, jobject object,
                                  jobject activity_thread) {
  // We obtain the LoadedApk of the running application, then update the
  // Application resource implementation with the resource implementation of the
  // LoadedApk.

  // We then call through to ActivityThread#handleApplicationInfoChanged() to
  // restart all activities.

  // Application app = activityThread.getApplication();
  // LoadedApk loadedApk = app.mLoadedApk;
  // Resources newResources = loadedApk.getResources();
  // ResourcesImpl newResourcesImpl = newResources.getImpl();
  // Resources oldResources = app.getResources();
  // oldResources.setImpl(newResourcesImpl);
  // ApplicationInfo appInfo = loadedApk.getApplicationInfo();
  // activityThread.handleApplicationInfoChanged(appInfo);

  JniObject thread_wrapper(jni, activity_thread);

  jobject app = thread_wrapper.CallMethod<jobject>(
      {"getApplication", "()Landroid/app/Application;"});
  JniObject app_wrapper(jni, app);

  JniObject loaded_apk = app_wrapper.GetField<JniObject>(
      {"mLoadedApk", "Landroid/app/LoadedApk;"});

  JniObject new_resources = loaded_apk.CallMethod<JniObject>(
      {"getResources", "()Landroid/content/res/Resources;"});

  jobject new_resources_impl = new_resources.CallMethod<jobject>(
      {"getImpl", "()Landroid/content/res/ResourcesImpl;"});

  JniObject old_resources = app_wrapper.CallMethod<JniObject>(
      {"getResources", "()Landroid/content/res/Resources;"});

  jvalue resources_arg{.l = new_resources_impl};
  old_resources.CallMethod<void>(
      {"setImpl", "(Landroid/content/res/ResourcesImpl;)V"}, &resources_arg);

  jobject app_info = loaded_apk.CallMethod<jobject>(
      {"getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;"});

  jvalue app_info_arg{.l = app_info};
  thread_wrapper.CallMethod<void>({"handleApplicationInfoChanged",
                                   "(Landroid/content/pm/ApplicationInfo;)V"},
                                  &app_info_arg);
}

}  // namespace deploy
