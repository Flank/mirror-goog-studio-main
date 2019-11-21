/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "app_inspection_service.h"
#include "unistd.h"
#include "utils/log.h"

using app_inspection::ServiceResponse;
using profiler::Log;

namespace app_inspection {

void EnqueueAppInspectionServiceResponse(JNIEnv *env, int32_t command_id,
                                         ServiceResponse::Status status,
                                         jstring error_message) {
  profiler::JStringWrapper message(env, error_message);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, status, message](profiler::proto::AgentService::Stub &stub,
                                     grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        event->set_pid(getpid());
        auto *inspection_event = event->mutable_app_inspection_event();
        inspection_event->set_command_id(command_id);
        auto *service_response = inspection_event->mutable_response();
        service_response->set_status(status);
        service_response->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

void EnqueueAppInspectionRawEvent(JNIEnv *env, int32_t command_id,
                                  jbyteArray event_data, int32_t length,
                                  jstring inspector_id) {
  profiler::JByteArrayWrapper data(env, event_data, length);
  profiler::JStringWrapper id(env, inspector_id);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, data, id](profiler::proto::AgentService::Stub &stub,
                              grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        event->set_pid(getpid());
        auto *inspection_event = event->mutable_app_inspection_event();
        inspection_event->set_command_id(command_id);
        auto *raw_response = inspection_event->mutable_raw_event();
        raw_response->set_inspector_id(id.get().c_str());
        raw_response->set_content(data.get());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

void EnqueueAppInspectionCrashEvent(JNIEnv *env, int32_t command_id,
                                    jstring inspector_id,
                                    jstring error_message) {
  profiler::JStringWrapper id(env, inspector_id);
  profiler::JStringWrapper message(env, error_message);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, id, message](profiler::proto::AgentService::Stub &stub,
                                 grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        event->set_pid(getpid());
        auto *inspection_event = event->mutable_app_inspection_event();
        inspection_event->set_command_id(command_id);
        auto *service_response = inspection_event->mutable_crash_event();
        service_response->set_inspector_id(id.get().c_str());
        service_response->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

jobject CreateAppInspectionService(JNIEnv *env) {
  auto service = AppInspectionService::create(env);
  if (service == nullptr) {
    return nullptr;
  }
  auto serviceClass = env->FindClass(
      "com/android/tools/agent/app/inspection/AppInspectionService");
  jmethodID constructor_method =
      env->GetMethodID(serviceClass, "<init>", "(J)V");
  return env->NewObject(serviceClass, constructor_method,
                        reinterpret_cast<jlong>(service));
}

#ifdef APP_INSPECTION_EXPERIMENT
static std::string ConvertClass(JNIEnv *env, jclass cls) {
  jclass classClass = env->FindClass("java/lang/Class");
  jmethodID mid =
      env->GetMethodID(classClass, "getCanonicalName", "()Ljava/lang/String;");

  jstring strObj = (jstring)env->CallObjectMethod(cls, mid);

  profiler::JStringWrapper name_wrapped(env, strObj);

  std::string name = "L" + name_wrapped.get() + ";";

  std::replace(name.begin(), name.end(), '.', '/');
  return name;
}

jobjectArray FindInstances(JNIEnv *env, jlong nativePtr, jclass jclass) {
  AppInspectionService *inspector =
      reinterpret_cast<AppInspectionService *>(nativePtr);
  return inspector->FindInstances(env, jclass);
}

void AddEntryTransformation(JNIEnv *env, jlong nativePtr, jclass origin_class,
                            jstring method_name) {
  AppInspectionService *inspector =
      reinterpret_cast<AppInspectionService *>(nativePtr);
  profiler::JStringWrapper method_str(env, method_name);
  std::size_t found = method_str.get().find("(");
  if (found == std::string::npos) {
    Log::E(
        Log::Tag::APPINSPECT,
        "Method should be in the format $method_name($signature)$return_type, "
        "but was %s",
        method_str.get().c_str());
    return;
  }

  inspector->AddEntryTransform(env, ConvertClass(env, origin_class),
                               method_str.get().substr(0, found),
                               method_str.get().substr(found));
}

void AddExitTransformation(JNIEnv *env, jlong nativePtr, jclass origin_class,
                           jstring method_name) {
  AppInspectionService *inspector =
      reinterpret_cast<AppInspectionService *>(nativePtr);
  profiler::JStringWrapper method_str(env, method_name);
  std::size_t found = method_str.get().find("(");
  if (found == std::string::npos) {
    Log::E(
        Log::Tag::APPINSPECT,
        "Method should be in the format $method_name($signature)$return_type, "
        "but was %s",
        method_str.get().c_str());
    return;
  }

  inspector->AddExitTransform(env, ConvertClass(env, origin_class),
                              method_str.get().substr(0, found),
                              method_str.get().substr(found));
}

#endif
}  // namespace app_inspection

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_replyError(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionServiceResponse(
      env, command_id, ServiceResponse::ERROR, error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_replySuccess(
    JNIEnv *env, jobject obj, jint command_id) {
  app_inspection::EnqueueAppInspectionServiceResponse(
      env, command_id, ServiceResponse::SUCCESS, nullptr);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_replyCrash(
    JNIEnv *env, jobject obj, jint command_id, jstring inspector_id,
    jstring error_message) {
  app_inspection::EnqueueAppInspectionCrashEvent(env, command_id, inspector_id,
                                                 error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_sendEvent(
    JNIEnv *env, jobject obj, jint command_id, jbyteArray event_data,
    jint length, jstring inspector_id) {
  app_inspection::EnqueueAppInspectionRawEvent(env, command_id, event_data,
                                               length, inspector_id);
}

JNIEXPORT jobject JNICALL
Java_com_android_tools_agent_app_inspection_AppInspectionService_createAppInspectionService(
    JNIEnv *env, jclass jclazz) {
  return app_inspection::CreateAppInspectionService(env);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_InspectorEnvironmentImpl_nativeRegisterEntryHook(
    JNIEnv *env, jclass jclazz, jlong servicePtr, jclass originClass,
    jstring originMethod) {
#ifdef APP_INSPECTION_EXPERIMENT
  app_inspection::AddEntryTransformation(env, servicePtr, originClass,
                                         originMethod);
#else
  Log::E(Log::Tag::APPINSPECT, "REGISTER ENTRY HOOK NOT IMPLEMENTED");
#endif
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_InspectorEnvironmentImpl_nativeRegisterExitHook(
    JNIEnv *env, jclass jclazz, jlong servicePtr, jclass originClass,
    jstring originMethod) {
#ifdef APP_INSPECTION_EXPERIMENT
  app_inspection::AddExitTransformation(env, servicePtr, originClass,
                                        originMethod);
#else
  Log::E(Log::Tag::APPINSPECT, "REGISTER EXIT HOOK NOT IMPLEMENTED");
#endif
}

JNIEXPORT jobjectArray JNICALL
Java_com_android_tools_agent_app_inspection_InspectorEnvironmentImpl_nativeFindInstances(
    JNIEnv *env, jclass callerClass, jlong servicePtr, jclass jclass) {
#ifdef APP_INSPECTION_EXPERIMENT
  return app_inspection::FindInstances(env, servicePtr, jclass);
#else
  Log::E(Log::Tag::APPINSPECT, "FIND INSTANCES NOT IMPLEMENTED");
  auto result = env->NewObjectArray(0, jclass, NULL);
  return result;
#endif
}
}
