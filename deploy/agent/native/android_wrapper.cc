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
 *
 */

#include "android_wrapper.h"

#include "jni/jni_class.h"
#include "jni/jni_object.h"

namespace swapper {

const char* AndroidWrapper::ACTIVITY_THREAD = "android/app/ActivityThread";
const char* AndroidWrapper::USER_HANDLE = "android/os/UserHandle";
const char* AndroidWrapper::PACKAGE_MANAGER =
    "android/content/pm/PackageManager";

const JniSignature AndroidWrapper::GET_PACKAGE_MANAGER = {
    "getPackageManager", "()Landroid/content/pm/IPackageManager;"};

const JniSignature AndroidWrapper::MY_USER_ID = {"myUserId", "()I"};

const JniSignature AndroidWrapper::GET_APPLICATION_INFO = {
    "getApplicationInfo",
    "(Ljava/lang/String;II)Landroid/content/pm/ApplicationInfo;"};

const JniSignature AndroidWrapper::CURRENT_ACTIVITY_THREAD = {
    "currentActivityThread", "()Landroid/app/ActivityThread;"};

const JniSignature AndroidWrapper::GET_APPLICATION_THREAD = {
    "getApplicationThread", "()Landroid/app/ActivityThread$ApplicationThread;"};

const JniSignature AndroidWrapper::SCHEDULE_APP_INFO_CHANGED = {
    "scheduleApplicationInfoChanged",
    "(Landroid/content/pm/ApplicationInfo;)V"};

const JniSignature AndroidWrapper::GET_SHARED_LIBRARY_FILES = {
    "GET_SHARED_LIBRARY_FILES", "I"};

void AndroidWrapper::RestartActivity(const char* package) {
  JniClass activityThread(jni_, ACTIVITY_THREAD);

  jvalue appInfoArgs[3];
  appInfoArgs[0].l = jni_->NewStringUTF(package);
  appInfoArgs[1].i = GetFlags();
  appInfoArgs[2].i = GetUserHandle();

  jobject applicationInfo =
      activityThread.CallStaticMethod<JniObject>(GET_PACKAGE_MANAGER)
          .CallMethod<jobject>(GET_APPLICATION_INFO, appInfoArgs);

  JniObject applicationThread =
      activityThread.CallStaticMethod<JniObject>(CURRENT_ACTIVITY_THREAD)
          .CallMethod<JniObject>(GET_APPLICATION_THREAD);

  jvalue appInfoChangedArgs = {.l = applicationInfo};
  applicationThread.CallMethod<void>(SCHEDULE_APP_INFO_CHANGED,
                                     &appInfoChangedArgs);

  // Release the string reference we created.
  jni_->DeleteLocalRef(appInfoArgs[0].l);
}

jint AndroidWrapper::GetUserHandle() {
  JniClass userHandle(jni_, USER_HANDLE);
  return userHandle.CallStaticMethod<jint>(MY_USER_ID);
}

jint AndroidWrapper::GetFlags() {
  JniClass packageManager(jni_, PACKAGE_MANAGER);
  return packageManager.GetStaticField<jint>(GET_SHARED_LIBRARY_FILES);
}

}  // namespace swapper