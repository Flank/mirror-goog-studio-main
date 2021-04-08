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
#include "app_inspection_common.h"
#include "app_inspection_service.h"
#include "unistd.h"
#include "utils/log.h"

using app_inspection::AppInspectionEvent;
using app_inspection::AppInspectionResponse;
using app_inspection::CreateInspectorResponse;
using app_inspection::LibraryCompatibilityInfo;
using profiler::Log;

namespace app_inspection {

// Create an ID that is unique across all inspectors attached to this process
int64_t CreateUniqueId() {
  static std::atomic_int id_generator_(1);

  int32_t uid = id_generator_++;
  return uid;
}

void EnqueueTransportEvent(
    JNIEnv *env,
    std::function<void(profiler::proto::Event *)> initialize_event) {
  profiler::Agent::Instance().SubmitAgentTasks(
      {[initialize_event](profiler::proto::AgentService::Stub &stub,
                          grpc::ClientContext &ctx) {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_is_ended(true);
        event->set_pid(getpid());
        initialize_event(event);
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

jlong EnqueueAppInspectionPayloadChunks(JNIEnv *env, jbyteArray data,
                                        int32_t length, int32_t chunk_size) {
  profiler::JByteArrayWrapper chunk_data(env, data, length);

  int32_t chunk_index = 0;
  int64_t payload_id = CreateUniqueId();
  while (true) {
    int32_t chunk_start = chunk_index * chunk_size;
    bool is_final_chunk = (chunk_start + chunk_size) >= length;
    EnqueueTransportEvent(env, [payload_id, chunk_data, chunk_start, chunk_size,
                                is_final_chunk](profiler::proto::Event *event) {
      event->set_kind(profiler::proto::Event::APP_INSPECTION_PAYLOAD);
      event->set_group_id(payload_id);
      event->set_is_ended(is_final_chunk);

      auto chunk = chunk_data.get().substr(chunk_start, chunk_size);
      auto *payload_event = event->mutable_app_inspection_payload();
      payload_event->set_chunk(chunk);
    });

    if (is_final_chunk) {
      break;
    }
    ++chunk_index;
  }
  return payload_id;
}

void EnqueueAppInspectionResponse(
    JNIEnv *env, int32_t command_id, AppInspectionResponse::Status status,
    jstring error_message,
    std::function<void(AppInspectionResponse *)> initialize_response) {
  profiler::JStringWrapper message(env, error_message);
  EnqueueTransportEvent(env, [command_id, status, message, initialize_response](
                                 profiler::proto::Event *event) {
    event->set_kind(profiler::proto::Event::APP_INSPECTION_RESPONSE);

    auto *inspection_response = event->mutable_app_inspection_response();
    inspection_response->set_command_id(command_id);
    inspection_response->set_status(status);
    inspection_response->set_error_message(message.get().c_str());
    initialize_response(inspection_response);
  });
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

void EnqueueAppInspectionRawResponseSuccess(JNIEnv *env, int32_t command_id,
                                            jbyteArray response_data,
                                            int32_t length) {
  profiler::JByteArrayWrapper data(env, response_data, length);
  EnqueueAppInspectionResponse(
      env, command_id, AppInspectionResponse::SUCCESS, nullptr,
      [data](AppInspectionResponse *response) {
        auto *raw_response = response->mutable_raw_response();
        raw_response->set_content(data.get());
      });
}

void EnqueueAppInspectionRawResponseError(JNIEnv *env, int32_t command_id,
                                          jstring error_message) {
  EnqueueAppInspectionResponse(env, command_id, AppInspectionResponse::ERROR,
                               error_message,
                               [](AppInspectionResponse *response) {});
}

void EnqueueAppInspectionRawResponseSuccess(JNIEnv *env, int32_t command_id,
                                            int64_t payload_id) {
  EnqueueAppInspectionResponse(
      env, command_id, AppInspectionResponse::SUCCESS, nullptr,
      [payload_id](AppInspectionResponse *response) {
        auto *raw_response = response->mutable_raw_response();
        raw_response->set_payload_id(payload_id);
      });
}

void EnqueueAppInspectionGetLibraryCompatibilityInfoResponse(
    JNIEnv *env, int32_t command_id, AppInspectionResponse::Status status,
    jobjectArray results, int32_t length, jstring error_message) {
  app_inspection::GetLibraryCompatibilityInfoResponse
      *get_library_compatibility_info_responses =
          new app_inspection::GetLibraryCompatibilityInfoResponse();
  for (int i = 0; i < length; ++i) {
    jobject result = env->GetObjectArrayElement(results, i);
    jclass result_class = env->GetObjectClass(result);
    // Get status field
    jfieldID status_field =
        env->GetFieldID(result_class, "status",
                        "Lcom/android/tools/agent/app/inspection/version/"
                        "CompatibilityCheckerResult$Status;");
    jobject status = env->GetObjectField(result, status_field);
    jmethodID ordinal_method = env->GetMethodID(
        env->FindClass("com/android/tools/agent/app/inspection/version/"
                       "CompatibilityCheckerResult$Status"),
        "ordinal", "()I");
    jint jstatus_value = env->CallIntMethod(status, ordinal_method);

    // Get message field
    jfieldID message_field =
        env->GetFieldID(result_class, "message", "Ljava/lang/String;");
    jstring jmessage = (jstring)env->GetObjectField(result, message_field);

    // Get the targeted library which contains min version.
    jfieldID targetLibraryField = env->GetFieldID(
        result_class, "artifactCoordinate", ARTIFACT_COORDINATE_TYPE.c_str());

    jobject jTargetLibrary = env->GetObjectField(result, targetLibraryField);

    jclass artifactCoordinateClass = env->GetObjectClass(jTargetLibrary);
    jfieldID group_id_field = env->GetFieldID(artifactCoordinateClass,
                                              "groupId", "Ljava/lang/String;");
    jstring jgroup_id =
        (jstring)env->GetObjectField(jTargetLibrary, group_id_field);

    jfieldID artifact_id_field = env->GetFieldID(
        artifactCoordinateClass, "artifactId", "Ljava/lang/String;");
    jstring jartifact_id =
        (jstring)env->GetObjectField(jTargetLibrary, artifact_id_field);

    jfieldID min_version_field = env->GetFieldID(
        artifactCoordinateClass, "version", "Ljava/lang/String;");
    jstring jmin_version =
        (jstring)env->GetObjectField(jTargetLibrary, min_version_field);

    // Get version string field
    jfieldID version_field =
        env->GetFieldID(result_class, "version", "Ljava/lang/String;");
    jstring jversion = (jstring)env->GetObjectField(result, version_field);

    auto *response = get_library_compatibility_info_responses->add_responses();
    switch (jstatus_value) {
      case 0:  // COMPATIBLE
        response->set_status(LibraryCompatibilityInfo::COMPATIBLE);
        break;
      case 1:  // INCOMPATIBLE
        response->set_status(LibraryCompatibilityInfo::INCOMPATIBLE);
        break;
      case 2:  // Missing library
        response->set_status(LibraryCompatibilityInfo::LIBRARY_MISSING);
        break;
      case 3:  // PROGUARDED
        response->set_status(LibraryCompatibilityInfo::APP_PROGUARDED);
        break;
      case 4:  // Error
        response->set_status(LibraryCompatibilityInfo::SERVICE_ERROR);
        break;
    }
    profiler::JStringWrapper message(env, jmessage);
    response->set_error_message(message.get().c_str());
    profiler::JStringWrapper version(env, jversion);
    response->set_version(version.get().c_str());
    auto *target = response->mutable_target_library();
    profiler::JStringWrapper group_id(env, jgroup_id);
    target->set_group_id(group_id.get().c_str());
    profiler::JStringWrapper artifact_id(env, jartifact_id);
    target->set_artifact_id(artifact_id.get().c_str());
    profiler::JStringWrapper min_version(env, jmin_version);
    target->set_version(min_version.get().c_str());
  }
  EnqueueAppInspectionResponse(
      env, command_id, status, error_message,
      [get_library_compatibility_info_responses](
          AppInspectionResponse *response) {
        response->set_allocated_get_library_compatibility_response(
            get_library_compatibility_info_responses);
      });
}

void EnqueueAppInspectionEvent(
    JNIEnv *env, jstring inspector_id,
    std::function<void(AppInspectionEvent *)> initialize_event) {
  profiler::JStringWrapper id(env, inspector_id);
  EnqueueTransportEvent(
      env, [id, initialize_event](profiler::proto::Event *event) {
        event->set_kind(profiler::proto::Event::APP_INSPECTION_EVENT);

        auto *inspection_event = event->mutable_app_inspection_event();
        inspection_event->set_inspector_id(id.get().c_str());
        initialize_event(inspection_event);
      });
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

void EnqueueAppInspectionRawEvent(JNIEnv *env, jstring inspector_id,
                                  jlong payload_id) {
  EnqueueAppInspectionEvent(env, inspector_id,
                            [payload_id](AppInspectionEvent *event) {
                              auto *raw_event = event->mutable_raw_event();
                              raw_event->set_payload_id(payload_id);
                            });
}

void EnqueueAppInspectionDisposedEvent(JNIEnv *env, jstring inspector_id,
                                       jstring error_message) {
  profiler::JStringWrapper message(env, error_message);
  EnqueueAppInspectionEvent(
      env, inspector_id, [message](AppInspectionEvent *event) {
        auto *disposed_event = event->mutable_disposed_event();
        disposed_event->set_error_message(message.get().c_str());
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

  inspector->AddEntryTransform(env, origin_class,
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

  inspector->AddExitTransform(env, origin_class,
                              method_str.get().substr(0, found),
                              method_str.get().substr(found));
}

}  // namespace app_inspection

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendPayload(
    JNIEnv *env, jobject obj, jbyteArray eventData, jint length,
    jint chunkSize) {
  return app_inspection::EnqueueAppInspectionPayloadChunks(env, eventData,
                                                           length, chunkSize);
}

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
Java_com_android_tools_agent_app_inspection_NativeTransport_sendCreateInspectorResponseAppProguarded(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionCreateInspectorResponse(
      env, command_id, AppInspectionResponse::ERROR, error_message,
      CreateInspectorResponse::APP_PROGUARDED);
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
  app_inspection::EnqueueAppInspectionRawResponseError(env, command_id,
                                                       error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawResponseData(
    JNIEnv *env, jobject obj, jint command_id, jbyteArray response_data,
    jint length) {
  app_inspection::EnqueueAppInspectionRawResponseSuccess(env, command_id,
                                                         response_data, length);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawResponsePayload(
    JNIEnv *env, jobject obj, jint command_id, jlong payload_id) {
  app_inspection::EnqueueAppInspectionRawResponseSuccess(env, command_id,
                                                         payload_id);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendGetLibraryCompatibilityInfoResponse(
    JNIEnv *env, jobject obj, jint command_id, jobjectArray results,
    jint length) {
  app_inspection::EnqueueAppInspectionGetLibraryCompatibilityInfoResponse(
      env, command_id, AppInspectionResponse::SUCCESS, results, length,
      nullptr);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendDisposedEvent(
    JNIEnv *env, jobject obj, jstring inspector_id, jstring error_message) {
  app_inspection::EnqueueAppInspectionDisposedEvent(env, inspector_id,
                                                    error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawEventData(
    JNIEnv *env, jobject obj, jstring inspector_id, jbyteArray event_data,
    jint length) {
  app_inspection::EnqueueAppInspectionRawEvent(env, inspector_id, event_data,
                                               length);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_NativeTransport_sendRawEventPayload(
    JNIEnv *env, jobject obj, jstring inspector_id, jlong payload_id) {
  app_inspection::EnqueueAppInspectionRawEvent(env, inspector_id, payload_id);
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
