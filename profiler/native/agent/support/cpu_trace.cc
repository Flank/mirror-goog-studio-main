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

#include <sstream>
#include <string>

#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "proto/internal_cpu.grpc.pb.h"
#include "utils/agent_task.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::Log;
using profiler::SteadyClock;
using profiler::proto::AgentService;
using profiler::proto::Command;
using profiler::proto::CpuTraceMode;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::CpuTraceOperationResponse;
using profiler::proto::CpuTraceType;
using profiler::proto::EmptyResponse;
using profiler::proto::InternalCpuService;
using profiler::proto::SendCommandRequest;
using profiler::proto::TraceInitiationType;
using std::string;

namespace {

constexpr char kTraceFileSuffix[] = "_api_initiated_trace";

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
  // when there is already an ongoing one), those arguments will be thrown away
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
  explicit TraceMonitor() {
    profiler::ProcessManager process_manager;
    app_name_ = process_manager.GetCmdlineForPid(getpid());
    Reset();
  }
  ~TraceMonitor() = delete;  // TODO: Support destroying the agent

  void Reset() {
    api_initiated_trace_in_progress_ = false;
    ongoing_start_request_.Clear();
    confirmed_trace_path_.clear();
  }

  // Helper method to read the trace content located at |trace_path| into
  // |trace_content|. Returns true if successful.
  bool ReadTraceContent(const string& trace_path, string* trace_content);

  SteadyClock clock_;
  string app_name_;
  bool api_initiated_trace_in_progress_;
  // Represents argument values as seen from the last start tracing API call.
  CpuTraceOperationRequest ongoing_start_request_;
  // Absolute path to the trace file with the correct extension. It is return
  // value of Debug.fixTracePath().
  string confirmed_trace_path_;
};

void TraceMonitor::RecordStartArguments(
    const CpuTraceOperationRequest& input_request) {
  ongoing_start_request_ = input_request;
}

void TraceMonitor::CheckFixTracePathCall(int32_t tid,
                                         const string& path_as_seen) {
  // This is a check for an Android framework assumption that fixTracePath is
  // called on the same thread as startMethodTracing.
  if (tid != ongoing_start_request_.thread_id()) {
    Log::E(
        Log::Tag::PROFILER,
        "startMethodTracing called from thread %d but fixTracePath enters from "
        "thread %d",
        ongoing_start_request_.thread_id(), tid);
  }
  if (path_as_seen != ongoing_start_request_.start().arg_trace_path()) {
    Log::E(
        Log::Tag::PROFILER,
        "startMethodTracing called with '%s' but fixTracePath called with '%s'",
        ongoing_start_request_.start().arg_trace_path().c_str(),
        path_as_seen.c_str());
  }
}

void TraceMonitor::SubmitStartEvent(int32_t tid, const string& fixed_path) {
  Log::D(Log::Tag::PROFILER, "TraceMonitor::SubmitStartEvent '%s'",
         fixed_path.c_str());
  if (api_initiated_trace_in_progress_) {
    Log::W(
        Log::Tag::PROFILER,
        "API-initiated tracing is already in progress; the call is ignored.");
  }

  // This is a check for an Android framework assumption that fixTracePath is
  // called on the same thread as startMethodTracing.
  if (tid != ongoing_start_request_.thread_id()) {
    Log::E(
        Log::Tag::PROFILER,
        "startMethodTracing called from thread %d but fixTracePath exits from "
        "thread %d",
        ongoing_start_request_.thread_id(), tid);
  }

  int64_t timestamp = clock_.GetCurrentTime();
  api_initiated_trace_in_progress_ = true;
  confirmed_trace_path_ = fixed_path;

  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[this, timestamp](AgentService::Stub& stub, ClientContext& ctx) {
          SendCommandRequest request;
          auto* command = request.mutable_command();
          command->set_type(Command::START_CPU_TRACE);
          command->set_pid(getpid());

          auto* start = command->mutable_start_cpu_trace();
          auto* metadata = start->mutable_api_start_metadata();
          metadata->set_start_timestamp(timestamp);

          auto* config = start->mutable_configuration();
          config->set_app_name(app_name_);
          config->set_initiation_type(TraceInitiationType::INITIATED_BY_API);

          auto* user_option = config->mutable_user_options();
          user_option->set_trace_type(CpuTraceType::ART);
          user_option->set_trace_mode(CpuTraceMode::INSTRUMENTED);

          EmptyResponse response;
          return stub.SendCommand(&ctx, request, &response);
        }});
  } else {
    Agent::Instance().SubmitCpuTasks(
        {[this, timestamp](InternalCpuService::Stub& stub, ClientContext& ctx) {
          int pid = getpid();
          CpuTraceOperationRequest request;
          request.CopyFrom(ongoing_start_request_);
          request.set_pid(pid);
          request.set_timestamp(timestamp);
          CpuTraceOperationResponse response;
          Status status = stub.SendTraceEvent(&ctx, request, &response);
          if (status.ok()) {
            if (!response.start_operation_allowed()) {
              // This start operation isn't allowed. Ignore it.
              Reset();
              Log::W(Log::Tag::PROFILER,
                     "Debug.startMethodTracing(String) called while tracing is "
                     "already in progress; the call is ignored.");
            }
          } else {
            // Not receiving a response from daemon. This task will be retried
            // so no-op here.
          }
          return status;
        }});
  }
}

bool TraceMonitor::ReadTraceContent(const string& trace_path,
                                    string* trace_content) {
  if (trace_path.empty()) {
    Log::E(Log::Tag::PROFILER,
           "Trace path not processed by fixTracePath() when "
           "stopMethodTracing() is called");
    return false;
  }

  profiler::FileReader::Read(trace_path, trace_content);
  return true;
}

void TraceMonitor::SubmitStopEvent(int tid) {
  if (!api_initiated_trace_in_progress_) return;

  int64_t timestamp = clock_.GetCurrentTime();
  int32_t pid = getpid();
  string trace_content;
  ReadTraceContent(confirmed_trace_path_, &trace_content);
  Log::D(Log::Tag::PROFILER, "TraceMonitor::SubmitStopEvent '%s' size=%zu",
         confirmed_trace_path_.c_str(), trace_content.size());
  // We are done with the cached data. Reset so that the next API tracing call
  // proceeds as normal while the tasks below run asynchonrously.
  Reset();

  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    // First, send the content of the trace file.
    std::ostringstream oss;
    oss << timestamp << kTraceFileSuffix;
    std::string payload_name{oss.str()};
    Agent::Instance().SubmitAgentTasks(
        profiler::CreateTasksToSendPayload(payload_name, trace_content, true));
    // Second, send the command to signal the recording is complete.
    Agent::Instance().SubmitAgentTasks(
        {[this, pid, timestamp, payload_name](AgentService::Stub& stub,
                                              ClientContext& ctx) mutable {
          SendCommandRequest request;
          auto* command = request.mutable_command();
          command->set_type(Command::STOP_CPU_TRACE);
          command->set_pid(pid);
          auto* stop = command->mutable_stop_cpu_trace();
          auto* metadata = stop->mutable_api_stop_metadata();
          metadata->set_stop_timestamp(timestamp);
          metadata->set_trace_name(payload_name);

          auto* config = stop->mutable_configuration();
          config->set_app_name(app_name_);
          config->set_initiation_type(TraceInitiationType::INITIATED_BY_API);

          auto* user_option = config->mutable_user_options();
          user_option->set_trace_type(CpuTraceType::ART);
          user_option->set_trace_mode(CpuTraceMode::INSTRUMENTED);

          EmptyResponse response;
          return stub.SendCommand(&ctx, request, &response);
        }});
  } else {
    Agent::Instance().SubmitCpuTasks(
        {[pid, tid, timestamp, trace_content](InternalCpuService::Stub& stub,
                                              ClientContext& ctx) {
          CpuTraceOperationRequest request;
          request.set_pid(pid);
          request.set_thread_id(tid);
          request.set_timestamp(timestamp);
          request.mutable_stop()->set_trace_content(trace_content);
          CpuTraceOperationResponse response;
          return stub.SendTraceEvent(&ctx, request, &response);
        }});
  }
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