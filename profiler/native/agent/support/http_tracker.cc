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
#include <cassert>
#include <memory>
#include <sstream>
#include <unordered_map>

#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JByteArrayWrapper;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::AgentService;
using profiler::proto::ChunkRequest;
using profiler::proto::EmptyNetworkReply;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::HttpEventRequest;
using profiler::proto::HttpRequestRequest;
using profiler::proto::HttpResponseRequest;
using profiler::proto::InternalNetworkService;
using profiler::proto::JavaThreadRequest;
using profiler::proto::SendBytesRequest;
using profiler::proto::SendEventRequest;

namespace {
std::atomic_int id_generator_(1);

constexpr char kRequestPayloadSuffix[] = "_request";
constexpr char kResponsePayloadSuffix[] = "_response";

// Intermediate buffer that stores all payload chunks not yet sent. That way,
// if grpc requests start to fall behind, data is batched and flushed all at
// once at the next opportunity. This can be a major performance boost, as it's
// faster to send one 10K message than ten 1K messages, which gives the system
// a chance to catch up.
struct PayloadBuffer {
  std::mutex payload_mutex;
  std::unordered_map<uint64_t, std::deque<std::string>> chunks;
  // Accumulative size of the paylod for the connection.
  // Note that entries for completed connections are not cleaned up.
  std::unordered_map<uint64_t, int32_t> payload_sizes;
  // Name suffix used for the payload
  const char *name_suffix;

  PayloadBuffer(const char *suffx) : name_suffix(suffx){};

  int32_t GetPayloadLength(jlong juid) {
    std::lock_guard<std::mutex> guard(payload_mutex);
    const auto &itr = payload_sizes.find(juid);
    return itr != payload_sizes.end() ? itr->second : 0;
  }

  void AddBytes(jlong juid, JByteArrayWrapper *bytes) {
    std::lock_guard<std::mutex> guard(payload_mutex);
    const auto &itr = chunks.find(juid);
    payload_sizes[juid] += bytes->length();
    if (itr != chunks.end()) {
      itr->second.push_back(bytes->get());
    } else {
      // We're pushing the first chunk onto the buffer, so also spawn a
      // background thread to consume it. Additional bytes reported before the
      // background thread finally runs will be sent out at the same time.
      chunks[juid].push_back(bytes->get());
      if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
        Agent::Instance().SubmitAgentTasks(
            {[juid, this](AgentService::Stub &stub, ClientContext &ctx) {
              std::ostringstream batched_bytes;
              {
                std::lock_guard<std::mutex> guard(payload_mutex);
                for (const std::string &chunk : chunks[juid]) {
                  batched_bytes << chunk;
                }
                chunks.erase(juid);
              }

              std::stringstream payload_name;
              payload_name << juid << name_suffix;
              SendBytesRequest request;
              request.set_name(payload_name.str());
              request.set_bytes(batched_bytes.str());
              request.set_is_partial(true);

              EmptyResponse response;
              Status result = stub.SendBytes(&ctx, request, &response);

              // Send failed, push the chunks back into front of deque
              if (!result.ok()) {
                std::lock_guard<std::mutex> guard(payload_mutex);
                chunks[juid].push_front(batched_bytes.str());
              }

              return result;
            }});
      } else {
        Agent::Instance().SubmitNetworkTasks({[juid, this](
            InternalNetworkService::Stub &stub, ClientContext &ctx) {
          std::ostringstream batched_bytes;
          {
            std::lock_guard<std::mutex> guard(payload_mutex);
            for (const std::string &chunk : chunks[juid]) {
              batched_bytes << chunk;
            }
            chunks.erase(juid);
          }

          ChunkRequest chunk;
          chunk.set_conn_id(juid);
          chunk.set_content(batched_bytes.str());
          if (name_suffix == kRequestPayloadSuffix) {
            chunk.set_type(ChunkRequest::REQUEST);
          } else if (name_suffix == kResponsePayloadSuffix) {
            chunk.set_type(ChunkRequest::RESPONSE);
          }

          EmptyNetworkReply reply;
          Status result = stub.SendChunk(&ctx, chunk, &reply);

          // Send failed, push the chunks back into front of deque
          if (!result.ok()) {
            std::lock_guard<std::mutex> guard(payload_mutex);
            chunks[juid].push_front(batched_bytes.str());
          }

          return result;
        }});
      }
    }
  }
};
PayloadBuffer response_payload_buffer_(kResponsePayloadSuffix);
PayloadBuffer request_payload_buffer_(kRequestPayloadSuffix);

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

void PrepopulateEventRequest(SendEventRequest *request, int64_t connection_id) {
  auto event = request->mutable_event();
  event->set_pid(getpid());
  event->set_group_id(connection_id);
  event->set_kind(Event::NETWORK_HTTP_CONNECTION);
  event->set_timestamp(GetClock().GetCurrentTime());
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
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_trackThread(
    JNIEnv *env, jobject thiz, jlong juid, jstring jthread_name,
    jlong jthread_id) {
  JStringWrapper thread_name(env, jthread_name);

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    int64_t timestamp = GetClock().GetCurrentTime();
    Agent::Instance().SubmitAgentTasks(
        {[timestamp, juid, thread_name, jthread_id](
            AgentService::Stub &stub, ClientContext &ctx) mutable {
          SendEventRequest request;
          auto *event = request.mutable_event();
          event->set_pid(getpid());
          event->set_group_id(juid);
          event->set_kind(Event::NETWORK_HTTP_THREAD);
          event->set_timestamp(timestamp);

          auto *data = event->mutable_network_http_thread();
          data->set_id(jthread_id);
          data->set_name(thread_name.get());

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  } else {
    Agent::Instance().SubmitNetworkTasks({[juid, thread_name, jthread_id](
        InternalNetworkService::Stub &stub, ClientContext &ctx) {
      JavaThreadRequest threadRequest;
      threadRequest.set_conn_id(juid);
      auto *thread = threadRequest.mutable_thread();
      thread->set_name(thread_name.get());
      thread->set_id(jthread_id);

      EmptyNetworkReply reply;
      return stub.TrackThread(&ctx, threadRequest, &reply);
    }});
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onClose(
    JNIEnv *env, jobject thiz, jlong juid) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    SendEventRequest request;
    PrepopulateEventRequest(&request, juid);
    std::stringstream ss;
    ss << juid << kResponsePayloadSuffix;
    std::string payload_name = ss.str();
    Agent::Instance().SubmitAgentTasks(
        {[payload_name](AgentService::Stub &stub, ClientContext &ctx) {
           // Sends an empty paylod with |is_partial| set to false to indicate
           // that the payload is complete.
           SendBytesRequest request;
           request.set_name(payload_name);
           request.set_is_partial(false);
           EmptyResponse response;
           return stub.SendBytes(&ctx, request, &response);
         },
         [request, juid, payload_name](AgentService::Stub &stub,
                                       ClientContext &ctx) mutable {
           auto *event = request.mutable_event();
           auto *data = event->mutable_network_http_connection()
                            ->mutable_http_response_completed();
           data->set_payload_id(payload_name);
           data->set_payload_size(
               response_payload_buffer_.GetPayloadLength(juid));
           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         },
         [request](AgentService::Stub &stub, ClientContext &ctx) mutable {
           auto *event = request.mutable_event();
           event->set_is_ended(true);
           auto *data =
               event->mutable_network_http_connection()->mutable_http_closed();
           data->set_completed(true);

           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         }});
  } else {
    EnqueueHttpEvent(juid, HttpEventRequest::DOWNLOAD_COMPLETED);
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_onReadBegin(
    JNIEnv *env, jobject thiz, jlong juid) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    // No-op. This is merged into onRequest.
  } else {
    EnqueueHttpEvent(juid, HttpEventRequest::DOWNLOAD_STARTED);
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024InputStreamTracker_reportBytes(
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes, jint jlen) {
  JByteArrayWrapper bytes(env, jbytes, jlen);
  response_payload_buffer_.AddBytes(juid, &bytes);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onClose(
    JNIEnv *env, jobject thiz, jlong juid) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    SendEventRequest request;
    PrepopulateEventRequest(&request, juid);
    std::stringstream ss;
    ss << juid << kRequestPayloadSuffix;
    std::string payload_name = ss.str();
    Agent::Instance().SubmitAgentTasks(
        {[payload_name](AgentService::Stub &stub, ClientContext &ctx) {
           // Sends an empty paylod with |is_partial| set to false to indicate
           // that the payload is complete.
           SendBytesRequest request;
           request.set_name(payload_name);
           request.set_is_partial(false);
           EmptyResponse response;
           return stub.SendBytes(&ctx, request, &response);
         },
         [request, juid, payload_name](AgentService::Stub &stub,
                                       ClientContext &ctx) mutable {
           auto *event = request.mutable_event();
           auto *data = event->mutable_network_http_connection()
                            ->mutable_http_request_completed();
           data->set_payload_id(payload_name);
           data->set_payload_size(
               request_payload_buffer_.GetPayloadLength(juid));
           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         }});
  } else {
    EnqueueHttpEvent(juid, HttpEventRequest::UPLOAD_COMPLETED);
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_onWriteBegin(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024OutputStreamTracker_reportBytes(
    JNIEnv *env, jobject thiz, jlong juid, jbyteArray jbytes, jint jlen) {
  JByteArrayWrapper bytes(env, jbytes, jlen);
  request_payload_buffer_.AddBytes(juid, &bytes);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onRequest(
    JNIEnv *env, jobject thiz, jlong juid, jstring jurl, jstring jstack,
    jstring jmethod, jstring jfields) {
  JStringWrapper url(env, jurl);
  JStringWrapper stack(env, jstack);
  JStringWrapper fields(env, jfields);
  JStringWrapper method(env, jmethod);

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    SendEventRequest request;
    PrepopulateEventRequest(&request, juid);
    Agent::Instance().SubmitAgentTasks({[request, url, stack, fields, method](
        AgentService::Stub &stub, ClientContext &ctx) mutable {
      auto *event = request.mutable_event();
      auto *data = event->mutable_network_http_connection()
                       ->mutable_http_request_started();
      data->set_url(url.get());
      data->set_trace(stack.get());
      data->set_fields(fields.get());
      data->set_method(method.get());

      EmptyResponse response;
      return stub.SendEvent(&ctx, request, &response);
    }});
  } else {
    int32_t pid = getpid();
    int64_t timestamp = GetClock().GetCurrentTime();
    Agent::Instance().SubmitNetworkTasks(
        {[juid, pid, timestamp, url, stack, fields, method](
            InternalNetworkService::Stub &stub, ClientContext &ctx) {
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
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onResponse(
    JNIEnv *env, jobject thiz, jlong juid, jstring jresponse, jstring jfields) {
  JStringWrapper fields(env, jfields);

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    SendEventRequest request;
    PrepopulateEventRequest(&request, juid);
    Agent::Instance().SubmitAgentTasks({[request, fields](
        AgentService::Stub &stub, ClientContext &ctx) mutable {
      auto *event = request.mutable_event();
      auto *data = event->mutable_network_http_connection()
                       ->mutable_http_response_started();
      data->set_fields(fields.get());

      EmptyResponse response;
      return stub.SendEvent(&ctx, request, &response);
    }});
  } else {
    Agent::Instance().SubmitNetworkTasks({[fields, juid](
        InternalNetworkService::Stub &stub, ClientContext &ctx) {
      HttpResponseRequest httpResponse;
      httpResponse.set_conn_id(juid);
      httpResponse.set_fields(fields.get());

      EmptyNetworkReply reply;
      return stub.SendHttpResponse(&ctx, httpResponse, &reply);
    }});
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onDisconnect(
    JNIEnv *env, jobject thiz, jlong juid) {}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_network_HttpTracker_00024Connection_onError(
    JNIEnv *env, jobject thiz, jlong juid, jstring jstatus) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    SendEventRequest request;
    PrepopulateEventRequest(&request, juid);
    Agent::Instance().SubmitAgentTasks(
        {[request](AgentService::Stub &stub, ClientContext &ctx) mutable {
          auto *event = request.mutable_event();
          event->set_is_ended(true);
          auto *data =
              event->mutable_network_http_connection()->mutable_http_closed();
          data->set_completed(false);

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  } else {
    EnqueueHttpEvent(juid, HttpEventRequest::ABORTED);
  }
}
};
