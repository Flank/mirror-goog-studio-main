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
using profiler::Agent;
using profiler::JByteArrayWrapper;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::ChunkRequest;
using profiler::proto::EmptyNetworkReply;
using profiler::proto::HttpEventRequest;
using profiler::proto::HttpRequestRequest;
using profiler::proto::HttpResponseRequest;
using profiler::proto::InternalNetworkService;
using profiler::proto::JavaThreadRequest;

namespace {
std::atomic_int id_generator_(1);

// Intermediate buffer that stores all payload chunks not yet sent. That way,
// if grpc requests start to fall behind, data is batched and flushed all at
// once at the next opportunity. This can be a major performance boost, as it's
// faster to send one 10K message than ten 1K messages, which gives the system
// a chance to catch up.
struct PayloadChunk {
  std::mutex chunks_mutex;
  std::unordered_map<uint64_t, std::deque<std::string>> chunks;

  void AddBytes(jlong juid, JByteArrayWrapper *bytes, ChunkRequest::Type type) {
    std::lock_guard<std::mutex> guard(chunks_mutex);
    const auto &itr = chunks.find(juid);
    if (itr != chunks.end()) {
      itr->second.push_back(bytes->get());
    } else {
      // We're pushing the first chunk onto the buffer, so also spawn a
      // background thread to consume it. Additional bytes reported before the
      // background thread finally runs will be sent out at the same time.
      chunks[juid].push_back(bytes->get());
      Agent::Instance().SubmitNetworkTasks(
          {[juid, this, type](InternalNetworkService::Stub &stub,
                              ClientContext &ctx) {
            std::ostringstream batched_bytes;
            {
              std::lock_guard<std::mutex> guard(chunks_mutex);
              for (const std::string &chunk : chunks[juid]) {
                batched_bytes << chunk;
              }
              chunks.erase(juid);
            }

            ChunkRequest chunk;
            chunk.set_conn_id(juid);
            chunk.set_content(batched_bytes.str());
            chunk.set_type(type);

            EmptyNetworkReply reply;
            Status result = stub.SendChunk(&ctx, chunk, &reply);

            // Send failed, push the chunks back into front of deque
            if (!result.ok()) {
              std::lock_guard<std::mutex> guard(chunks_mutex);
              chunks[juid].push_front(batched_bytes.str());
            }

            return result;
          }});
    }
  }
};
PayloadChunk response_payload_chunk_;
PayloadChunk request_payload_chunk_;

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
  Agent::Instance().SubmitNetworkTasks(
      {[uid, event, timestamp](InternalNetworkService::Stub &stub,
                               ClientContext &ctx) {
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

  Agent::Instance().SubmitNetworkTasks(
      {[juid, thread_name, jthread_id](InternalNetworkService::Stub &stub,
                                       ClientContext &ctx) {
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
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes, jint jlen) {
  JByteArrayWrapper bytes(env, jbytes, jlen);
  response_payload_chunk_.AddBytes(juid, &bytes, ChunkRequest::RESPONSE);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onClose(
    JNIEnv *env, jobject thiz, jlong juid) {
  EnqueueHttpEvent(juid, HttpEventRequest::UPLOAD_COMPLETED);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onWriteBegin(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_reportBytes(
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes, jint jlen) {
  JByteArrayWrapper bytes(env, jbytes, jlen);
  request_payload_chunk_.AddBytes(juid, &bytes, ChunkRequest::REQUEST);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequest(
    JNIEnv *env, jobject thiz, jlong juid, jstring jurl, jstring jstack, jstring jmethod, jstring jfields) {
  JStringWrapper url(env, jurl);
  JStringWrapper stack(env, jstack);
  JStringWrapper fields(env, jfields);
  JStringWrapper method(env, jmethod);

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Agent::Instance().SubmitNetworkTasks(
      {[juid, pid, timestamp, url, stack, fields, method](InternalNetworkService::Stub &stub,
                              ClientContext &ctx) {
        HttpRequestRequest httpRequest;
        httpRequest.set_conn_id(juid);
        httpRequest.set_start_timestamp(timestamp);
        httpRequest.set_pid(pid);
        httpRequest.set_url(url.get());
        httpRequest.set_trace(stack.get());
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
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onDisconnect(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onError(
    JNIEnv *env, jobject thiz, jlong juid, jstring jstatus) {
  EnqueueHttpEvent(juid, HttpEventRequest::ABORTED);
}
};
