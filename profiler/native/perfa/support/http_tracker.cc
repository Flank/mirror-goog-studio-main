/*
 * Copyright (C) 2009 The Android Open Source Project
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
#include <string.h>
#include <jni.h>

#include "utils/log.h"

using profiler::Log;

extern "C" {
  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onClose(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_CloseInput [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onReadBegin(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_ReadInput [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onClose(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_CloseOutput [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onWriteBegin(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_WriteOutput [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onPreConnect(
      JNIEnv *env, jobject thiz, jstring jurl, jstring jstack)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    const char *stack = env->GetStringUTFChars(jstack, NULL);
    Log::V("HTTP_PreConnect [%s]\n%s", url, stack);
    env->ReleaseStringUTFChars(jstack, stack);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequestBody(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_RequestBody [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequest(
      JNIEnv *env, jobject thiz, jstring jurl, jstring jmethod, jstring jfields)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    const char *method = env->GetStringUTFChars(jmethod, NULL);
    const char *fields = env->GetStringUTFChars(jfields, NULL);
    Log::V("HTTP_Request (%s) [%s]\n%s", method, url, fields);
    env->ReleaseStringUTFChars(jfields, fields);
    env->ReleaseStringUTFChars(jmethod, method);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponse(
      JNIEnv *env, jobject thiz, jstring jurl, jstring jresponse, jstring jfields)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    const char *response = env->GetStringUTFChars(jresponse, NULL);
    const char *fields = env->GetStringUTFChars(jfields, NULL);
    Log::V("HTTP_Response (%s) [%s]\n%s", response, url, fields);
    env->ReleaseStringUTFChars(jfields, fields);
    env->ReleaseStringUTFChars(jresponse, response);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponseBody(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_ResponseBody [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onDisconnect(
      JNIEnv *env, jobject thiz, jstring jurl)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    Log::V("HTTP_Disconnect [%s]", url);
    env->ReleaseStringUTFChars(jurl, url);
  }

  JNIEXPORT void JNICALL
  Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onError(
      JNIEnv *env, jobject thiz, jstring jurl, jstring jstatus)
  {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    const char *status = env->GetStringUTFChars(jstatus, NULL);
    Log::V("HTTP_Error [%s]\n%s", url, status);
    env->ReleaseStringUTFChars(jstatus, status);
    env->ReleaseStringUTFChars(jurl, url);
  }
};
