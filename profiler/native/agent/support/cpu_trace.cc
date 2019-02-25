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
 */
#include <jni.h>
#include <unistd.h>

#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "proto/internal_cpu.grpc.pb.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::Log;
using profiler::SteadyClock;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::CpuTraceOperationResponse;
using profiler::proto::InternalCpuService;
using std::string;

namespace {

class TraceMonitor {
 public:
  // Grabs the singleton instance of the Agent. This will initialize the class
  // if necessary.
  static TraceMonitor& Instance() {
    static TraceMonitor* instance = new TraceMonitor();
    return *instance;
  }

  // Called at the entry of various Debug.startMethodTracing(..) calls.
  // Records arguments as seen in a start tracing call. Arugments have been
  // packaged into |input_request|. If the call is invalid (e.g., starting trace
  // when there is already an ongoing one), those arguments will be throw away
  // in SubmitStartEvent().
  void RecordStartArguments(const CpuTraceOperationRequest& input_request);
  // Called at the entry of Debug.fixTracePath().
  // Checks if Debug.fixTracePath() is called as expected. |path_as_seen| is the
  // argument value passed into the API.
  void CheckFixTracePathCall(int32_t tid, const string& path_as_seen);
  // Called at the exit of Debug.fixTracePath().
  // Submits the start event to perfd. |fixed_path| is the return value from
  // Debug.fixTracePath(). May throw away start arguments if the last start call
  // is invalid (e.g., starting trace when there is already an ongoing one).
  void SubmitStartEvent(int32_t tid, const string& fixed_path);
  // Called at the exit of Debug.stopMethodTracing().
  // Obtains the trace content and submits the stop event to perfd.
  void SubmitStopEvent(int tid);

 private:
  // Use TraceMonitor::Instance() to initialize.
  explicit TraceMonitor() { Reset(); }
  ~TraceMonitor() = delete;  // TODO: Support destroying the agent

  void Reset() {
    api_initiated_trace_in_progress_ = false;
    trace_id_ = -1;
    unconfirmed_start_request_.Clear();
    confirmed_trace_path_.clear();
  }

  SteadyClock clock_;
  // Absolute path of the file being created and written by the app.
  bool api_initiated_trace_in_progress_;
  int trace_id_;
  // Represents argument values as seen from the last start tracing API call.
  // If the call is invalid (e.g., starting trace when there is already an
  // ongoing one), those fields will be throw away.
  CpuTraceOperationRequest unconfirmed_start_request_;
  // Absolute path to the trace file with the correct extension. It is return
  // value of Debug.fixTracePath().
  string confirmed_trace_path_;
};

void TraceMonitor::RecordStartArguments(
    const CpuTraceOperationRequest& input_request) {
  unconfirmed_start_request_ = input_request;
}

void TraceMonitor::CheckFixTracePathCall(int32_t tid,
                                         const string& path_as_seen) {
  if (tid != unconfirmed_start_request_.thread_id()) {
    Log::E(
        "startMethodTracing called from thread %d but fixTracePath enters from "
        "thread %d",
        unconfirmed_start_request_.thread_id(), tid);
  }
  if (path_as_seen != unconfirmed_start_request_.start().arg_trace_path()) {
    Log::E(
        "startMethodTracing called with '%s' but fixTracePath called with '%s'",
        unconfirmed_start_request_.start().arg_trace_path().c_str(),
        path_as_seen.c_str());
  }
}

void TraceMonitor::SubmitStartEvent(int32_t tid, const string& fixed_path) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    return;
  }

  if (tid != unconfirmed_start_request_.thread_id()) {
    Log::E(
        "startMethodTracing called from thread %d but fixTracePath exits from "
        "thread %d",
        unconfirmed_start_request_.thread_id(), tid);
  }

  int64_t timestamp = clock_.GetCurrentTime();
  Agent::Instance().SubmitCpuTasks({[this, fixed_path, timestamp](
                                        InternalCpuService::Stub& stub,
                                        ClientContext& ctx) {
    int pid = getpid();
    CpuTraceOperationRequest request;
    request.CopyFrom(unconfirmed_start_request_);
    request.set_pid(pid);
    request.set_timestamp(timestamp);
    CpuTraceOperationResponse response;
    Status status = stub.SendTraceEvent(&ctx, request, &response);
    if (status.ok()) {
      if (response.start_operation_allowed()) {
        api_initiated_trace_in_progress_ = true;
        trace_id_ = response.trace_id();
        confirmed_trace_path_ = fixed_path;
      } else {
        // This start operation isn't allowed. Ignore it.
        unconfirmed_start_request_.Clear();
        Log::W(
            "Debug.startMethodTracing(String) called while tracing is already "
            "in progress; the call is ignored.");
      }
    } else {
      // Not receiving a response from perfd. Since the profiling state is
      // unknown, ignore this start operation.
    }
    return status;
  }});
}

void TraceMonitor::SubmitStopEvent(int tid) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    return;
  }

  if (!api_initiated_trace_in_progress_) return;
  int64_t timestamp = clock_.GetCurrentTime();
  Agent::Instance().SubmitCpuTasks(
      {[this, tid, timestamp](InternalCpuService::Stub& stub,
                              ClientContext& ctx) {
        CpuTraceOperationRequest request;
        int pid = getpid();
        request.set_pid(pid);
        request.set_thread_id(tid);
        request.set_timestamp(timestamp);
        string trace_content;
        if (confirmed_trace_path_.empty()) {
          Log::E(
              "Trace path not processed by fixTracePath() when "
              "stopMethodTracing() is called");
        } else {
          profiler::FileReader::Read(confirmed_trace_path_, &trace_content);
        }
        request.mutable_stop()->set_trace_id(trace_id_);
        request.mutable_stop()->set_trace_content(trace_content);
        CpuTraceOperationResponse response;
        Status status = stub.SendTraceEvent(&ctx, request, &response);
        Reset();
        return status;
      }});
}

}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_sendStartOperation(
    JNIEnv* env, jclass clazz, jint thread_id, jstring trace_path) {
  CpuTraceOperationRequest request;
  request.set_thread_id(thread_id);
  request.mutable_start()->set_method_name("startMethodTracing");
  request.mutable_start()->set_method_signature("(Ljava/lang/String;)V");
  JStringWrapper path_string(env, trace_path);
  request.mutable_start()->set_arg_trace_path(path_string.get());
  TraceMonitor::Instance().RecordStartArguments(request);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_recordInputPath(
    JNIEnv* env, jclass clazz, jint thread_id, jstring input_path) {
  JStringWrapper path_string(env, input_path);
  TraceMonitor::Instance().CheckFixTracePathCall(thread_id, path_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_recordOutputPath(
    JNIEnv* env, jclass clazz, jint thread_id, jstring output_path) {
  JStringWrapper path_string(env, output_path);
  TraceMonitor::Instance().SubmitStartEvent(thread_id, path_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_sendStopOperation(
    JNIEnv* env, jclass clazz, jint thread_id) {
  TraceMonitor::Instance().SubmitStopEvent(thread_id);
}
};