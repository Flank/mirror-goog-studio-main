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

using app_inspection::AppInspectionEvent;
using app_inspection::AppInspectionResponse;
using app_inspection::CreateInspectorResponse;
using profiler::Log;

namespace app_inspection {

void EnqueueAppInspectionResponse(
    JNIEnv *env, int32_t command_id, AppInspectionResponse::Status status,
    jstring error_message,
    std::function<void(AppInspectionResponse *)> initialize_response) {
  profiler::JStringWrapper message(env, error_message);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, status, message, initialize_response](
           profiler::proto::AgentService::Stub &stub,
           grpc::ClientContext &ctx) {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION_RESPONSE);
        event->set_is_ended(true);
        event->set_pid(getpid());
        auto *inspection_response = event->mutable_app_inspection_response();
        inspection_response->set_command_id(command_id);
        inspection_response->set_status(status);
        inspection_response->set_error_message(message.get().c_str());
        initialize_response(inspection_response);
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

void EnqueueAppInspectionDisposeInspectorResponse(
    JNIEnv *env, int32_t command_id, AppInspectionResponse::Status status,
    jstring error_message) {
  EnqueueAppInspectionResponse(env, command_id, status, error_message,
                               [](AppInspectionResponse *response) {
                                 response->mutable_dispose_inspector_response();
                               });
}

void EnqueueAppInspectionCreateInspectorResponse(
    JNIEnv *env, int32_t command_id, AppInspectionResponse::Status status,
    jstring error_message, CreateInspectorResponse::Status create_status) {
  EnqueueAppInspectionResponse(
      env, command_id, status, error_message,
      [create_status](AppInspectionResponse *response) {
        auto *create_inspector_response =
            response->mutable_create_inspector_response();
        create_inspector_response->set_status(create_status);
      });
}

void EnqueueAppInspectionRawResponse(JNIEnv *env, int32_t command_id,
                                     AppInspectionResponse::Status status,
                                     jbyteArray response_data, int32_t length,
                                     jstring error_message) {
  if (status == AppInspectionResponse::SUCCESS) {
    profiler::JByteArrayWrapper data(env, response_data, length);
    EnqueueAppInspectionResponse(env, command_id, status, error_message,
                                 [data](AppInspectionResponse *response) {
                                   auto *raw_response =
                                       response->mutable_raw_response();
                                   raw_response->set_content(data.get());
                                 });
  } else {
    EnqueueAppInspectionResponse(env, command_id, status, error_message,
                                 [](AppInspectionResponse *response) {
                                   auto *raw_response =
                                       response->mutable_raw_response();
                                 });
  }
}

void EnqueueAppInspectionEvent(
    JNIEnv *env, jstring inspector_id,
    std::function<void(AppInspectionEvent *)> initialize_event) {
  profiler::JStringWrapper id(env, inspector_id);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[id, initialize_event](profiler::proto::AgentService::Stub &stub,
                              grpc::ClientContext &ctx) {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION_EVENT);
        event->set_is_ended(true);
        event->set_pid(getpid());
        auto *inspection_event = event->mutable_app_inspection_event();
        inspection_event->set_inspector_id(id.get().c_str());
        initialize_event(inspection_event);
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

void EnqueueAppInspectionRawEvent(JNIEnv *env, jstring inspector_id,
                                  jbyteArray event_data, int32_t length) {
  profiler::JByteArrayWrapper data(env, event_data, length);
  EnqueueAppInspectionEvent(env, inspector_id,
                            [data](AppInspectionEvent *event) {
                              auto *raw_event = event->mutable_raw_event();
                              raw_event->set_content(data.get());
                            });
}

void EnqueueAppInspectionCrashEvent(JNIEnv *env, jstring inspector_id,
                                    jstring error_message) {
  profiler::JStringWrapper message(env, error_message);
  EnqueueAppInspectionEvent(
      env, inspector_id, [message](AppInspectionEvent *event) {
        auto *crash_event = event->mutable_crash_event();
        crash_event->set_error_message(message.get().c_str());
      });
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

}  // namespace app_inspection

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCreateInspectorResponseSuccess(
    JNIEnv *env, jobject obj, jint command_id) {
  app_inspection::EnqueueAppInspectionCreateInspectorResponse(
      env, command_id, AppInspectionResponse::SUCCESS, nullptr,
      CreateInspectorResponse::SUCCESS);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCreateInspectorResponseError(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionCreateInspectorResponse(
      env, command_id, AppInspectionResponse::ERROR, error_message,
      CreateInspectorResponse::GENERIC_SERVICE_ERROR);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCreateInspectorResponseVersionIncompatible(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionCreateInspectorResponse(
      env, command_id, AppInspectionResponse::ERROR, error_message,
      CreateInspectorResponse::VERSION_INCOMPATIBLE);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCreateInspectorResponseLibraryMissing(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionCreateInspectorResponse(
      env, command_id, AppInspectionResponse::ERROR, error_message,
      CreateInspectorResponse::LIBRARY_MISSING);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendDisposeInspectorResponseSuccess(
    JNIEnv *env, jobject obj, jint command_id) {
  app_inspection::EnqueueAppInspectionDisposeInspectorResponse(
      env, command_id, AppInspectionResponse::SUCCESS, nullptr);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendDisposeInspectorResponseError(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionDisposeInspectorResponse(
      env, command_id, AppInspectionResponse::ERROR, error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawResponseError(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionRawResponse(
      env, command_id, AppInspectionResponse::ERROR, nullptr, 0, error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawResponseSuccess(
    JNIEnv *env, jobject obj, jint command_id, jbyteArray response_data,
    jint length) {
  app_inspection::EnqueueAppInspectionRawResponse(
      env, command_id, AppInspectionResponse::SUCCESS, response_data, length,
      nullptr);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCrashEvent(
    JNIEnv *env, jobject obj, jstring inspector_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionCrashEvent(env, inspector_id,
                                                 error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawEvent(
    JNIEnv *env, jobject obj, jstring inspector_id, jbyteArray event_data,
    jint length) {
  app_inspection::EnqueueAppInspectionRawEvent(env, inspector_id, event_data,
                                               length);
}

JNIEXPORT jobject JNICALL
Java_com_android_tools_agent_app_inspection_AppInspectionService_createAppInspectionService(
    JNIEnv *env, jclass jclazz) {
  return app_inspection::CreateAppInspectionService(env);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_AppInspectionService_nativeRegisterEntryHook(
    JNIEnv *env, jclass jclazz, jlong servicePtr, jclass originClass,
    jstring originMethod) {
  app_inspection::AddEntryTransformation(env, servicePtr, originClass,
                                         originMethod);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_AppInspectionService_nativeRegisterExitHook(
    JNIEnv *env, jclass jclazz, jlong servicePtr, jclass originClass,
    jstring originMethod) {
  app_inspection::AddExitTransformation(env, servicePtr, originClass,
                                        originMethod);
}

JNIEXPORT jobjectArray JNICALL
Java_com_android_tools_agent_app_inspection_ArtToolingImpl_nativeFindInstances(
    JNIEnv *env, jclass callerClass, jlong servicePtr, jclass jclass) {
  return app_inspection::FindInstances(env, servicePtr, jclass);
}
}
