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

// Obtain the LoadedApk of the current application, then replace the
// application's ResourcesImpl with the ResourcesImpl of the LoadedApk. Return
// the resource implementation of the LoadedApk so that it can be used to fix
// activity contexts.
jobject Native_FixAppContext(JNIEnv* jni, jobject object,
                             jobject activity_thread) {
  // Application app = activityThread.getApplication();
  // LoadedApk loadedApk = app.mLoadedApk;
  // Resources newResources = loadedApk.getResources();
  // ResourcesImpl newResourcesImpl = newResources.getImpl();
  // Resources oldResources = app.getResources();
  // oldResources.setImpl(newResourcesImpl);
  // ApplicationInfo appInfo = loadedApk.getApplicationInfo();
  // activityThread.handleApplicationInfoChanged(appInfo);
  // return newResourcesImpl;

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

  jvalue arg{.l = new_resources_impl};
  old_resources.CallMethod<void>(
      {"setImpl", "(Landroid/content/res/ResourcesImpl;)V"}, &arg);

  return new_resources_impl;
}

// Get the list of ActivityClientRecords so that we can reach into each
// activity and update its internal ResourceImpl.
jobject Native_GetActivityClientRecords(JNIEnv* jni, jobject object,
                                        jobject activity_thread) {
  // ArrayMap<IBinder, ActivityClientRecord> map = activityThread.mActivities;
  // return map.values();

  JniObject thread_wrapper(jni, activity_thread);

  JniObject map = thread_wrapper.GetField<JniObject>(
      {"mActivities", "Landroid/util/ArrayMap;"});

  return map.CallMethod<jobject>({"values", "()Ljava/util/Collection;"});
}

// Given an ActivityRecord, replace the resource implementation of the activity
// with a new ResourcesImpl.
void Native_FixActivityContext(JNIEnv* jni, jobject object,
                               jobject activity_record,
                               jobject new_resources_impl) {
  // Activity activity = activityRecord.activity;
  // Resources oldResources = activity.getResources();
  // oldResources.setImpl(newResourcesImpl);

  JniObject record_wrapper(jni, activity_record);

  JniObject activity = record_wrapper.GetField<JniObject>(
      {"activity", "Landroid/app/Activity;"});

  JniObject old_resources = activity.CallMethod<JniObject>(
      {"getResources", "()Landroid/content/res/Resources;"});

  jvalue arg{.l = new_resources_impl};
  old_resources.CallMethod<void>(
      {"setImpl", "(Landroid/content/res/ResourcesImpl;)V"}, &arg);
}

// Call handleUpdateApplicationInfo changed on the current activity thread,
// using the LoadedApk of the current application.
void Native_UpdateApplicationInfo(JNIEnv* jni, jobject object,
                                  jobject activity_thread) {
  // Application app = activityThread.getApplication();
  // LoadedApk loadedApk = app.mLoadedApk;
  // ApplicationInfo appInfo = loadedApk.getApplicationInfo();
  // activityThread.handleApplicationInfoChanged(appInfo);

  JniObject thread_wrapper(jni, activity_thread);

  jobject app = thread_wrapper.CallMethod<jobject>(
      {"getApplication", "()Landroid/app/Application;"});
  JniObject app_wrapper(jni, app);

  JniObject loaded_apk = app_wrapper.GetField<JniObject>(
      {"mLoadedApk", "Landroid/app/LoadedApk;"});

  jobject app_info = loaded_apk.CallMethod<jobject>(
      {"getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;"});

  jvalue arg{.l = app_info};
  thread_wrapper.CallMethod<void>({"handleApplicationInfoChanged",
                                   "(Landroid/content/pm/ApplicationInfo;)V"},
                                  &arg);
}

}  // namespace deploy
