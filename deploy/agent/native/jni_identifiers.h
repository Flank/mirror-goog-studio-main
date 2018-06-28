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

namespace swapper {

const char* ACTIVITY_THREAD = "android/app/ActivityThread";
const char* ACTIVITY_THREAD_HANDLER = "android/app/ActivityThread$H";
const char* USER_HANDLE = "android/os/UserHandle";
const char* PACKAGE_MANAGER = "android/content/pm/PackageManager";

// Member function in ActivityThread$H.
const JniSignature HANDLE_MESSAGE = {"handleMessage",
                                     "(Landroid/os/Message;)V"};

// Static function in ActivityThread.
const JniSignature GET_PACKAGE_MANAGER = {
    "getPackageManager", "()Landroid/content/pm/IPackageManager;"};

// Static function in UserHandle.
const JniSignature MY_USER_ID = {"myUserId", "()I"};

// Member function in PackageManager.
const JniSignature GET_APPLICATION_INFO = {
    "getApplicationInfo",
    "(Ljava/lang/String;II)Landroid/content/pm/ApplicationInfo;"};

// Static function in ActivityThread.
const JniSignature CURRENT_ACTIVITY_THREAD = {"currentActivityThread",
                                              "()Landroid/app/ActivityThread;"};

// Member function in ActivityThread.
const JniSignature GET_APPLICATION_THREAD = {
    "getApplicationThread", "()Landroid/app/ActivityThread$ApplicationThread;"};

// Member function in ApplicationThread.
const JniSignature SCHEDULE_APP_INFO_CHANGED = {
    "scheduleApplicationInfoChanged",
    "(Landroid/content/pm/ApplicationInfo;)V"};

// Static field in ActivityThread$H.
const JniSignature APP_INFO_CHANGED = {"APPLICATION_INFO_CHANGED", "I"};

// Static field in PackageManager.
const JniSignature GET_SHARED_LIBRARY_FILES = {"GET_SHARED_LIBRARY_FILES", "I"};

// Member field in Message.
const JniSignature MESSAGE_WHAT = {"what", "I"};

}  // namespace swapper