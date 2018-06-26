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

#include "jni.h"
#include "jvmti.h"

#include <string>

#include "capabilities.h"
#include "hotswap.h"
#include "jni_class.h"
#include "jni_identifiers.h"
#include "jni_object.h"
#include "jni_util.h"

#include "utils/log.h"

using std::string;

namespace swapper {

// TODO: Make the agent more C++ like and not use globals.
string dexdir("");

// Retrieve the current user ID.
jint GetUserHandle(JNIEnv* jni) {
  JniClass userHandle(jni, USER_HANDLE);
  return userHandle.CallStatic<jint>(MY_USER_ID);
}

// Retrieve the flags needed to get application info.
jint GetFlags(JNIEnv* jni) {
  JniClass packageManager(jni, PACKAGE_MANAGER);
  return packageManager.GetStaticField<jint>(GET_SHARED_LIBRARY_FILES);
}

// Enqueue an application info changed event.
void ScheduleAppInfoChanged(JNIEnv* jni, jvalue* appInfoArgs) {
  JniClass activityThread(jni, ACTIVITY_THREAD);

  JniObject applicationInfo =
      activityThread.CallStatic<JniObject>(GET_PACKAGE_MANAGER)
          .Call<JniObject>(GET_APPLICATION_INFO, appInfoArgs);

  JniObject applicationThread =
      activityThread.CallStatic<JniObject>(CURRENT_ACTIVITY_THREAD)
          .Call<JniObject>(GET_APPLICATION_THREAD);

  jvalue args = {.l = applicationInfo.GetJObject()};
  applicationThread.Call<void>(SCHEDULE_APP_INFO_CHANGED, &args);
}

extern "C" void JNICALL MethodEntry(jvmtiEnv* jvmti, JNIEnv* jni,
                                    jthread current_thread, jmethodID method) {
  static jmethodID handler = 0;
  static jint messageSlot = 0;
  static jint appInfoChanged = 0;

  // Do this work once. It's safe to hold onto method ids and constant values.
  if (handler == 0) {
    JniClass activityThreadH(jni, ACTIVITY_THREAD_HANDLER);

    handler = activityThreadH.GetMethodID(HANDLE_MESSAGE);
    messageSlot = GetLocalVariableSlot(jvmti, handler, "msg");
    appInfoChanged = activityThreadH.GetStaticField<jint>(APP_INFO_CHANGED);
  }

  // If we're not in the correct method, return quickly. This handler is called
  // for every method entry that occurs in the JVM; thus, even if it will only
  // be on for a short time, it is probably best to exit early.
  if (method != handler) {
    return;
  }

  // Retrieve the message object from the current (0th) stack frame.
  jobject msg;
  jvmti->GetLocalObject(current_thread, 0, messageSlot, &msg);

  // Check to see if the message is an application info changed message.
  JniObject message(jni, msg);
  if (message.GetField<jint>(MESSAGE_WHAT) != appInfoChanged) {
    return;
  }

  HotSwap codeswap(jvmti, jni);
  // If hot swap fails, translate the message into an exit event. This is
  // for illustrative purposes only; in reality, we'll change it to a -1.
  if (!codeswap.DoHotSwap(dexdir)) {
    Log::V("Changing event from app-info update to app close.");
    message.SetField(MESSAGE_WHAT, 111);
    jvmti->SetLocalObject(current_thread, 0, messageSlot, message.GetJObject());
  }

  // Disable this event, because keeping it on is expensive.
  jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY,
                                  NULL);
  // We can't detach from the VM, so just relinquish all our capabilities.
  jvmti->RelinquishCapabilities(&CODE_AND_RESOURCE_SWAP);
}

// Initializer for JVMTI and JNI.
bool Initialize(JavaVM* vm, jvmtiEnv*& jvmti, JNIEnv*& jni) {
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    Log::E("Error initializing JVMTI");
    return false;
  }

  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    Log::E("Error initializing JNI");
    return false;
  }

  // Always assume code and resource swap, for now.
  if (jvmti->AddCapabilities(&CODE_AND_RESOURCE_SWAP) != JVMTI_ERROR_NONE) {
    Log::E("Error setting capabilities");
    return false;
  }

  jvmtiEventCallbacks callbacks;
  callbacks.MethodEntry = MethodEntry;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)) !=
      JVMTI_ERROR_NONE) {
    Log::E("Error setting event callbacks");
    return false;
  }

  return true;
}

// Event that fires when the agent hooks onto a running VM.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* input,
                                                 void* reserved) {
  jvmtiEnv* jvmti;
  JNIEnv* jni;

  if (!Initialize(vm, jvmti, jni)) {
    Log::E("Could not start agent");
    return JNI_ERR;
  }

  Log::V("Agent started");

  // TODO(acleung): Find a better way to pass arguments to the agent. May a PB?
  // For now we just comma split options...
  string options(input);

  // Argument #1: The package to update.
  auto pos = options.find(',');
  if (pos == string::npos) {
    Log::E(
        "Invalid agruements to start instant run agent: %s"
        "Expecting <appid>,<dex_location>",
        input);
    return JNI_ERR;
  }
  string appid = options.substr(0, pos);

  // Argument #2: The directory on the device that contains all the dex files to
  dexdir = options.substr(pos + 1, options.length());

  Log::V("app id %s", appid.c_str());
  if (appid.length() > 0 && appid != "<none>") {
    // Schedule update app-info first. It will handle hotswap when at the call
    // back.
    jvalue args[3];
    args[0].l = jni->NewStringUTF(appid.c_str());
    args[1].i = GetFlags(jni);
    args[2].i = GetUserHandle(jni);

    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY,
                                    NULL);
    ScheduleAppInfoChanged(jni, args);
  } else {
    // Otherwise, directly hotswap right here.
    HotSwap codeswap(jvmti, jni);
    if (!codeswap.DoHotSwap(dexdir)) {
      // TODO: Return meaningful status.
      return JNI_ERR;
    }
  }

  return JNI_OK;
}

}  // namespace swapper
