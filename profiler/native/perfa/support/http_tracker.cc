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
#include <jni.h>
#include <string.h>
#include <unistd.h>

#include "perfa/perfa.h"
#include "perfa/support/jni_types.h"
#include "utils/clock.h"
#include "utils/log.h"

#include <atomic>

using grpc::ClientContext;
using profiler::JByteArrayWrapper;
using profiler::JStringWrapper;
using profiler::Log;
using profiler::Perfa;
using profiler::SteadyClock;
using profiler::proto::ChunkRequest;
using profiler::proto::HttpDataRequest;
using profiler::proto::HttpEventRequest;
using profiler::proto::EmptyNetworkReply;

namespace {
std::atomic_int id_generator_(1);

void SendHttpEvent(uint64_t uid, HttpEventRequest::Event event) {
  auto net_stub = Perfa::Instance().network_stub();

  SteadyClock clock;
  ClientContext ctx;
  HttpEventRequest httpEvent;
  EmptyNetworkReply reply;

  httpEvent.set_conn_id(uid);
  httpEvent.set_timestamp(clock.GetCurrentTime());
  httpEvent.set_event(event);

  net_stub.SendHttpEvent(&ctx, httpEvent, &reply);
}
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_nextId(
    JNIEnv *env, jobject thiz) {
  int32_t app_id = getpid();
  int32_t local_id = id_generator_++;

  int64_t uid = app_id;
  uid <<= 32;
  uid |= local_id;

  return uid;
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onClose(
    JNIEnv *env, jobject thiz, jlong juid) {
  SendHttpEvent(juid, HttpEventRequest::DOWNLOAD_COMPLETED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onReadBegin(
    JNIEnv *env, jobject thiz, jlong juid) {
  SendHttpEvent(juid, HttpEventRequest::DOWNLOAD_STARTED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_reportBytes(
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes) {
  auto net_stub = Perfa::Instance().network_stub();

  ClientContext ctx;
  ChunkRequest chunk;
  EmptyNetworkReply response;

  JByteArrayWrapper bytes(env, jbytes);

  chunk.set_conn_id(juid);
  chunk.set_content(bytes.get());
  chunk.set_type(ChunkRequest::RESPONSE);

  net_stub.SendChunk(&ctx, chunk, &response);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onClose(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onWriteBegin(
    JNIEnv *env, jobject thiz, jlong juid) {
  // TODO: Report request body upload started
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onPreConnect(
    JNIEnv *env, jobject thiz, jlong juid, jstring jurl, jstring jstack) {
  JStringWrapper url(env, jurl);
  JStringWrapper stack(env, jstack); // TODO: Send this to perfd

  auto net_stub = Perfa::Instance().network_stub();
  ClientContext ctx;
  HttpDataRequest httpData;
  EmptyNetworkReply reply;
  httpData.set_conn_id(juid);
  httpData.set_app_id(getpid());
  httpData.set_url(url.get());
  net_stub.RegisterHttpData(&ctx, httpData, &reply);

  SendHttpEvent(juid, HttpEventRequest::CREATED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequestBody(
    JNIEnv *env, jobject thiz, jlong juid) {
  // TODO: Report request body upload bytes
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequest(
    JNIEnv *env, jobject thiz, jlong juid, jstring jmethod, jstring jfields) {
  // TODO: Report request code and fields
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponse(
    JNIEnv *env, jobject thiz, jlong juid, jstring jresponse, jstring jfields) {
  // TODO: Report reponse code and fields
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponseBody(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onDisconnect(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onError(
    JNIEnv *env, jobject thiz, jlong juid, jstring jstatus) {
  SendHttpEvent(juid, HttpEventRequest::ABORTED);
}
};
