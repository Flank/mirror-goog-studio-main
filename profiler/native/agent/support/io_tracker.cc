/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include <unistd.h>

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::EmptyIoReply;
using profiler::proto::InternalIoService;
using profiler::proto::IoCallRequest;
using profiler::proto::IoSessionEndRequest;
using profiler::proto::IoSessionStartRequest;

namespace {
std::atomic_int id_generator_(1);

const SteadyClock &GetClock() {
  static SteadyClock clock;
  return clock;
}
}  // namespace

extern "C" {
// This is implemented on the native side so the interceptors can retrieve
// device time before calling the actual methods of the I/O-related Java classes
// and use it as start timestamp.
JNIEXPORT jlong JNICALL
Java_com_android_tools_profiler_support_io_IoTracker_getTimeInNanos(
    JNIEnv *env, jobject thiz) {
  return GetClock().GetCurrentTime();
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_profiler_support_io_IoTracker_nextId(JNIEnv *env,
                                                            jobject thiz) {
  int32_t app_id = getpid();
  int32_t local_id = id_generator_++;

  int64_t uid = app_id;
  uid <<= 32;
  uid |= local_id;

  return uid;
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_io_IoTracker_trackNewFileSession(
    JNIEnv *env, jobject thiz, jlong jsession_id, jstring jfile_path) {
  JStringWrapper file_path(env, jfile_path);
  int32_t pid = getpid();
  int64_t timestamp = GetClock().GetCurrentTime();

  Agent::Instance().SubmitIoTasks(
      {[pid, jsession_id, file_path, timestamp](InternalIoService::Stub &stub,
                                                ClientContext &ctx) {
        IoSessionStartRequest io_session_start_request;
        io_session_start_request.set_process_id(pid);
        io_session_start_request.set_io_session_id(jsession_id);
        io_session_start_request.set_file_path(file_path.get());
        io_session_start_request.set_timestamp(timestamp);
        EmptyIoReply reply;
        return stub.TrackIoSessionStart(&ctx, io_session_start_request, &reply);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_io_IoTracker_trackIoCall(
    JNIEnv *env, jobject thiz, jlong jsession_id, jint jnumber_of_bytes,
    jlong jstart_timestamp, jboolean jread) {
  int32_t pid = getpid();
  int64_t end_timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitIoTasks(
      {[pid, jsession_id, jnumber_of_bytes, jstart_timestamp, end_timestamp,
        jread](InternalIoService::Stub &stub, ClientContext &ctx) {
        IoCallRequest io_call_request;
        io_call_request.set_process_id(pid);
        io_call_request.set_io_session_id(jsession_id);
        io_call_request.set_bytes_count(jnumber_of_bytes);
        io_call_request.set_start_timestamp(jstart_timestamp);
        io_call_request.set_end_timestamp(end_timestamp);
        io_call_request.set_type(jread ? profiler::proto::READ
                                       : profiler::proto::WRITE);
        EmptyIoReply reply;
        return stub.TrackIoCall(&ctx, io_call_request, &reply);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_io_IoTracker_trackTerminatingFileSession(
    JNIEnv *env, jobject thiz, jlong jsession_id) {
  int32_t pid = getpid();
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitIoTasks(
      {[pid, jsession_id, timestamp](InternalIoService::Stub &stub,
                                     ClientContext &ctx) {
        IoSessionEndRequest io_session_end_request;
        io_session_end_request.set_process_id(pid);
        io_session_end_request.set_io_session_id(jsession_id);
        io_session_end_request.set_timestamp(timestamp);
        EmptyIoReply reply;
        return stub.TrackIoSessionEnd(&ctx, io_session_end_request, &reply);
      }});
}
};
