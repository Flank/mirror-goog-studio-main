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
#include <atomic>
#include <memory>

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using profiler::JByteArrayWrapper;
using profiler::JStringWrapper;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::ChunkRequest;
using profiler::proto::HttpDataRequest;
using profiler::proto::HttpEventRequest;
using profiler::proto::HttpRequestRequest;
using profiler::proto::HttpResponseRequest;
using profiler::proto::EmptyNetworkReply;

namespace {
std::atomic_int id_generator_(1);

const SteadyClock &GetClock() {
  static SteadyClock clock;
  return clock;
}

void SendHttpEvent(uint64_t uid, int64_t timestamp,
                   HttpEventRequest::Event event) {
  auto net_stub = Agent::Instance().network_stub();

  ClientContext ctx;
  HttpEventRequest httpEvent;
  EmptyNetworkReply reply;

  httpEvent.set_conn_id(uid);
  httpEvent.set_timestamp(timestamp);
  httpEvent.set_event(event);

  net_stub.SendHttpEvent(&ctx, httpEvent, &reply);
}

void EnqueueHttpEvent(uint64_t uid, HttpEventRequest::Event event) {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().background_queue()->EnqueueTask(
      [uid, event, timestamp] { SendHttpEvent(uid, timestamp, event); });
}

}  // namespace

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
  EnqueueHttpEvent(juid, HttpEventRequest::DOWNLOAD_COMPLETED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onReadBegin(
    JNIEnv *env, jobject thiz, jlong juid) {
  EnqueueHttpEvent(juid, HttpEventRequest::DOWNLOAD_STARTED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_reportBytes(
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes) {
  JByteArrayWrapper bytes(env, jbytes);

  Agent::Instance().background_queue()->EnqueueTask([bytes, juid] {
    auto net_stub = Agent::Instance().network_stub();

    ClientContext ctx;
    EmptyNetworkReply reply;
    ChunkRequest chunk;

    chunk.set_conn_id(juid);
    chunk.set_content(bytes.get());
    chunk.set_type(ChunkRequest::RESPONSE);

    net_stub.SendChunk(&ctx, chunk, &reply);
  });
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
  JStringWrapper stack(env, jstack);  // TODO: Send this to perfd

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Agent::Instance().background_queue()->EnqueueTask(
      [juid, pid, stack, timestamp, url] {
        auto net_stub = Agent::Instance().network_stub();
        ClientContext ctx;
        HttpDataRequest httpData;
        EmptyNetworkReply reply;
        httpData.set_conn_id(juid);
        httpData.set_process_id(pid);
        httpData.set_url(url.get());
        httpData.set_trace(stack.get());
        httpData.set_start_timestamp(timestamp);
        net_stub.RegisterHttpData(&ctx, httpData, &reply);

        SendHttpEvent(juid, timestamp, HttpEventRequest::CREATED);
      });
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequestBody(
    JNIEnv *env, jobject thiz, jlong juid) {
  // TODO: Report request body upload bytes
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequest(
    JNIEnv *env, jobject thiz, jlong juid, jstring jmethod, jstring jfields) {
  JStringWrapper fields(env, jfields);
  JStringWrapper method(env, jmethod);

  Agent::Instance().background_queue()->EnqueueTask([fields, juid, method] {
    auto net_stub = Agent::Instance().network_stub();
    ClientContext ctx;
    HttpRequestRequest httpRequest;
    EmptyNetworkReply reply;

    httpRequest.set_conn_id(juid);
    httpRequest.set_fields(fields.get());
    httpRequest.set_method(method.get());
    net_stub.SendHttpRequest(&ctx, httpRequest, &reply);
  });
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponse(
    JNIEnv *env, jobject thiz, jlong juid, jstring jresponse, jstring jfields) {
  JStringWrapper fields(env, jfields);

  Agent::Instance().background_queue()->EnqueueTask([fields, juid] {
    auto net_stub = Agent::Instance().network_stub();

    ClientContext ctx;
    HttpResponseRequest httpResponse;
    EmptyNetworkReply reply;

    httpResponse.set_conn_id(juid);
    httpResponse.set_fields(fields.get());
    net_stub.SendHttpResponse(&ctx, httpResponse, &reply);
  });
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
  EnqueueHttpEvent(juid, HttpEventRequest::ABORTED);
}
};
