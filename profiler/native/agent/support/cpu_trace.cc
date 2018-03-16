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
#include "agent/support/jni_wrappers.h"
#include "proto/internal_cpu.grpc.pb.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JStringWrapper;
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

  void SubmitStartEvent(const CpuTraceOperationRequest& input_request);
  void SubmitStopEvent(int tid);

 private:
  // Use TraceMonitor::Instance() to initialize.
  explicit TraceMonitor() {}
  ~TraceMonitor() = delete;  // TODO: Support destroying the agent

  SteadyClock clock_;
  // Absolute path of the file being created and written by the app.
  string trace_path_;
  int trace_id_;
  bool api_initiated_trace_in_progress_{false};
};

void TraceMonitor::SubmitStartEvent(
    const CpuTraceOperationRequest& input_request) {
  api_initiated_trace_in_progress_ = true;
  int64_t timestamp = clock_.GetCurrentTime();
  Agent::Instance().SubmitCpuTasks(
      {[this, input_request, timestamp](InternalCpuService::Stub& stub,
                                        ClientContext& ctx) {
        int pid = getpid();
        CpuTraceOperationRequest request;
        request.CopyFrom(input_request);
        request.set_pid(pid);
        request.set_timestamp(timestamp);
        CpuTraceOperationResponse response;
        Status status = stub.SendTraceEvent(&ctx, request, &response);
        if (status.ok()) {
          trace_id_ = response.trace_id();

          // TODO(b/74405724): Get the absolute path properly.
          string absolute_path{"/sdcard/Android/data/"};
          string app_pkg_name = profiler::ProcessManager::GetCmdlineForPid(pid);
          absolute_path.append(app_pkg_name)
              .append("/files/")
              .append(request.start().arg_trace_path());
          trace_path_ = absolute_path;
        } else {
          // Not receiving a trace id. Ignore this catpure.
          api_initiated_trace_in_progress_ = false;
        }
        return status;
      }});
}

void TraceMonitor::SubmitStopEvent(int tid) {
  if (!api_initiated_trace_in_progress_) return;
  api_initiated_trace_in_progress_ = false;
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
        profiler::FileReader::Read(trace_path_, &trace_content);
        request.mutable_stop()->set_trace_id(trace_id_);
        request.mutable_stop()->set_trace_content(trace_content);
        CpuTraceOperationResponse response;
        Status status = stub.SendTraceEvent(&ctx, request, &response);
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
  TraceMonitor::Instance().SubmitStartEvent(request);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_sendStopOperation(
    JNIEnv* env, jclass clazz, jint thread_id) {
  TraceMonitor::Instance().SubmitStopEvent(thread_id);
}
};