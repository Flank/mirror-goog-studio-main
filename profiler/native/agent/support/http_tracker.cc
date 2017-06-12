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
#include <sstream>
#include <unordered_map>

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
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
using profiler::proto::JavaThreadRequest;
using profiler::proto::InternalNetworkService;

namespace {
std::atomic_int id_generator_(1);

// Intermediate buffer that stores all payload chunks not yet sent. That way,
// if grpc requests start to fall behind, data is batched and flushed all at
// once at the next opportunity. This can be a major performance boost, as it's
// faster to send one 10K message than ten 1K messages, which gives the system
// a chance to catch up.
std::mutex chunks_mutex_;
std::unordered_map<uint64_t, std::deque<std::string>> chunks_;

const SteadyClock &GetClock() {
  static SteadyClock clock;
  return clock;
}

Status SendHttpEvent(InternalNetworkService::Stub &stub, ClientContext &ctx,
                     uint64_t uid, int64_t timestamp,
                     HttpEventRequest::Event event) {
  HttpEventRequest httpEvent;
  httpEvent.set_conn_id(uid);
  httpEvent.set_timestamp(timestamp);
  httpEvent.set_event(event);

  EmptyNetworkReply reply;
  return stub.SendHttpEvent(&ctx, httpEvent, &reply);
}

void EnqueueHttpEvent(uint64_t uid, HttpEventRequest::Event event) {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitNetworkTasks({[uid, event, timestamp](
      InternalNetworkService::Stub &stub, ClientContext &ctx) {
    return SendHttpEvent(stub, ctx, uid, timestamp, event);
  }});
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
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_trackThread(
    JNIEnv *env, jobject thiz, jlong juid, jstring jthread_name,
    jlong jthread_id) {
  JStringWrapper thread_name(env, jthread_name);

  Agent::Instance().SubmitNetworkTasks({[juid, thread_name, jthread_id](
      InternalNetworkService::Stub &stub, ClientContext &ctx) {
    JavaThreadRequest threadRequest;
    threadRequest.set_conn_id(juid);
    auto thread = threadRequest.mutable_thread();
    thread->set_name(thread_name.get());
    thread->set_id(jthread_id);

    EmptyNetworkReply reply;
    return stub.TrackThread(&ctx, threadRequest, &reply);
  }});
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

  {
    std::lock_guard<std::mutex> guard(chunks_mutex_);
    const auto &itr = chunks_.find(juid);
    if (itr != chunks_.end()) {
      itr->second.push_back(bytes.get());
    } else {
      // We're pushing the first chunk onto the buffer, so also spawn a
      // background thread to consume it. Additional bytes reported before the
      // background thread finally runs will be sent out at the same time.
      chunks_[juid].push_back(bytes.get());
      Agent::Instance().SubmitNetworkTasks(
          {[juid](InternalNetworkService::Stub &stub, ClientContext &ctx) {
            std::ostringstream batched_bytes;
            {
              std::lock_guard<std::mutex> guard(chunks_mutex_);
              for (const std::string &chunk : chunks_[juid]) {
                batched_bytes << chunk;
              }
              chunks_.erase(juid);
            }

            ChunkRequest chunk;
            chunk.set_conn_id(juid);
            chunk.set_content(batched_bytes.str());
            chunk.set_type(ChunkRequest::RESPONSE);

            EmptyNetworkReply reply;
            Status result = stub.SendChunk(&ctx, chunk, &reply);

            // Send failed, push the chunks back into front of deque
            if (!result.ok()) {
              std::lock_guard<std::mutex> guard(chunks_mutex_);
              chunks_[juid].push_front(batched_bytes.str());
            }

            return result;
          }});
    }
  }
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
  Agent::Instance().SubmitNetworkTasks(
      {[juid, pid, stack, timestamp, url](InternalNetworkService::Stub &stub,
                                          ClientContext &ctx) {
         HttpDataRequest httpData;
         httpData.set_conn_id(juid);
         httpData.set_process_id(pid);
         httpData.set_url(url.get());
         httpData.set_trace(stack.get());
         httpData.set_start_timestamp(timestamp);

         EmptyNetworkReply reply;
         return stub.RegisterHttpData(&ctx, httpData, &reply);
       },
       [juid, timestamp](InternalNetworkService::Stub &stub,
                         ClientContext &ctx) {
         return SendHttpEvent(stub, ctx, juid, timestamp,
                              HttpEventRequest::CREATED);
       }});
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

  Agent::Instance().SubmitNetworkTasks({[fields, juid, method](
      InternalNetworkService::Stub &stub, ClientContext &ctx) {
    HttpRequestRequest httpRequest;
    httpRequest.set_conn_id(juid);
    httpRequest.set_fields(fields.get());
    httpRequest.set_method(method.get());

    EmptyNetworkReply reply;
    return stub.SendHttpRequest(&ctx, httpRequest, &reply);
  }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponse(
    JNIEnv *env, jobject thiz, jlong juid, jstring jresponse, jstring jfields) {
  JStringWrapper fields(env, jfields);

  Agent::Instance().SubmitNetworkTasks(
      {[fields, juid](InternalNetworkService::Stub &stub, ClientContext &ctx) {
        HttpResponseRequest httpResponse;
        httpResponse.set_conn_id(juid);
        httpResponse.set_fields(fields.get());

        EmptyNetworkReply reply;
        return stub.SendHttpResponse(&ctx, httpResponse, &reply);
      }});
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
