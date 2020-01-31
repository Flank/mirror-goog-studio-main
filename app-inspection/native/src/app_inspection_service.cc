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
#include "app_inspection_service.h"

#ifdef APP_INSPECTION_EXPERIMENT
#include <functional>
#include <unordered_map>
#endif

#include "agent/jvmti_helper.h"
#ifdef APP_INSPECTION_EXPERIMENT
#include "app_inspection_transform.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "utils/device_info.h"
#endif

#include "utils/log.h"

using namespace profiler;

namespace app_inspection {

AppInspectionService* AppInspectionService::create(JNIEnv* env) {
  JavaVM* vm;
  int error = env->GetJavaVM(&vm);
  if (error != 0) {
    Log::E(Log::Tag::APPINSPECT,
           "Failed to get JavaVM instance for AppInspectionService with error "
           "code: %d",
           error);
    return nullptr;
  }
  // This will attach the current thread to the vm, otherwise
  // CreateJvmtiEnv(vm) below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);
  // Create a stand-alone jvmtiEnv to avoid any callback conflicts
  // with other profilers' agents.
  jvmtiEnv* jvmti = CreateJvmtiEnv(vm);
  if (jvmti == nullptr) {
    Log::E(Log::Tag::APPINSPECT,
           "Failed to initialize JVMTI env for AppInspectionService");
    return nullptr;
  }
  auto service = new AppInspectionService(jvmti);

#ifdef APP_INSPECTION_EXPERIMENT
  service->Initialize();
#endif
  return service;
}

AppInspectionService::AppInspectionService(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

#ifdef APP_INSPECTION_EXPERIMENT

static jvmtiIterationControl JNICALL HeapObjectCallback(jlong class_tag,
                                                        jlong size,
                                                        jlong* tag_ptr,
                                                        void* user_data) {
  jlong tag = *(reinterpret_cast<jlong*>(user_data));
  *tag_ptr = tag;
  return JVMTI_ITERATION_CONTINUE;
}

jobjectArray AppInspectionService::FindInstances(JNIEnv* jni, jclass clazz) {
  if (jni->IsSameObject(clazz, jni->FindClass("java/lang/Class"))) {
    // Special-case handling for the Class object. Internally, ART creates many
    // dummy Class objects that we don't care about. Calling GetLoadedClasses
    // returns us the list of the real Class instances.
    jint count;
    jclass* classes;

    if (CheckJvmtiError(jvmti_, jvmti_->GetLoadedClasses(&count, &classes))) {
      return jni->NewObjectArray(0, clazz, NULL);
    }

    auto result = jni->NewObjectArray(count, clazz, NULL);
    for (int i = 0; i < count; ++i) {
      jni->SetObjectArrayElement(result, i, (jobject)classes[i]);
    }
    jvmti_->Deallocate((unsigned char*)classes);

    return result;
  }

  jvmtiPhase phase_ptr;
  jvmti_->GetPhase(&phase_ptr);

  jlong tag = nextTag;
  if (CheckJvmtiError(jvmti_, jvmti_->IterateOverInstancesOfClass(
                                  clazz, JVMTI_HEAP_OBJECT_EITHER,
                                  HeapObjectCallback, &tag))) {
    return jni->NewObjectArray(0, clazz, NULL);
  }
  nextTag++;

  jint count;
  jobject* instances;
  if (CheckJvmtiError(jvmti_, jvmti_->GetObjectsWithTags(1, &tag, &count,
                                                         &instances, NULL))) {
    return jni->NewObjectArray(0, clazz, NULL);
  }

  auto result = jni->NewObjectArray(count, clazz, NULL);
  for (int i = 0; i < count; ++i) {
    jni->SetObjectArrayElement(result, i, instances[i]);
  }
  jvmti_->Deallocate((unsigned char*)instances);

  return result;
}

class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti_env) : jvmti_env_(jvmti_env) {}

  virtual void* Allocate(size_t size) {
    return profiler::Allocate(jvmti_env_, size);
  }

  virtual void Free(void* ptr) { profiler::Deallocate(jvmti_env_, ptr); }

 private:
  jvmtiEnv* jvmti_env_;
};

std::unordered_map<std::string, AppInspectionTransform*>*
GetAppInspectionTransforms() {
  static auto* transformations =
      new std::unordered_map<std::string, AppInspectionTransform*>();
  return transformations;
}

// ClassPrepare event callback to invoke transformation of selected classes.
// In pre-P, this saves expensive OnClassFileLoaded calls for other classes.
void AppInspectionService::OnClassPrepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                                          jthread thread, jclass klass) {
  char* sig_mutf8;
  jvmti_env->GetClassSignature(klass, &sig_mutf8, nullptr);
  auto class_transforms = GetAppInspectionTransforms();
  if (class_transforms->find(sig_mutf8) != class_transforms->end()) {
    CheckJvmtiError(
        jvmti_env, jvmti_env->SetEventNotificationMode(
                       JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, &klass));
    CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                   JVMTI_DISABLE,
                                   JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
  }
  if (sig_mutf8 != nullptr) {
    jvmti_env->Deallocate((unsigned char*)sig_mutf8);
  }
}

void AppInspectionService::OnClassFileLoaded(
    jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass class_being_redefined,
    jobject loader, const char* name, jobject protection_domain,
    jint class_data_len, const unsigned char* class_data,
    jint* new_class_data_len, unsigned char** new_class_data) {
  // The tooling interface will specify class names like "java/net/URL"
  // however, in .dex these classes are stored using the "Ljava/net/URL;"
  // format.
  std::string desc = "L" + std::string(name) + ";";
  auto class_transforms = GetAppInspectionTransforms();
  auto transform = class_transforms->find(desc);
  if (transform == class_transforms->end()) return;

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::V(Log::Tag::APPINSPECT, "Could not find class index for %s", name);
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  transform->second->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti_env);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
}

void AppInspectionService::Initialize() {
  SetAllCapabilities(jvmti_);
  jvmtiError error;
  CheckJvmtiError(jvmti_, error);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;

  callbacks.ClassPrepare = OnClassPrepare;
  CheckJvmtiError(jvmti_,
                  jvmti_->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Before P ClassFileLoadHook has significant performance overhead so we
  // only enable the hook during retransformation (on agent attach and class
  // prepare). For P+ we want to keep the hook events always on to support
  // multiple retransforming agents (and therefore don't need to perform
  // retransformation on class prepare).
  bool filter_class_load_hook = DeviceInfo::feature_level() < DeviceInfo::P;
  SetEventNotification(jvmti_,
                       filter_class_load_hook ? JVMTI_ENABLE : JVMTI_DISABLE,
                       JVMTI_EVENT_CLASS_PREPARE);
  SetEventNotification(jvmti_,
                       filter_class_load_hook ? JVMTI_DISABLE : JVMTI_ENABLE,
                       JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);
}

void AppInspectionService::AddTransform(JNIEnv* jni,
                                        const std::string& class_name,
                                        const std::string& method_name,
                                        const std::string& signature,
                                        bool is_entry) {
  auto class_transforms = GetAppInspectionTransforms();
  auto transform_iter = class_transforms->find(class_name);
  AppInspectionTransform* app_transform;
  if (transform_iter == class_transforms->end()) {
    app_transform = new AppInspectionTransform(class_name.c_str());
    class_transforms->insert({class_name, app_transform});
  } else {
    app_transform = transform_iter->second;
  }
  app_transform->AddTransform(class_name.c_str(), method_name.c_str(),
                              signature.c_str(), is_entry);

  jclass found_class = nullptr;
  jint class_count;
  jclass* loaded_classes;
  char* sig_mutf8;
  jvmti_->GetLoadedClasses(&class_count, &loaded_classes);
  for (int i = 0; i < class_count; ++i) {
    jvmti_->GetClassSignature(loaded_classes[i], &sig_mutf8, nullptr);
    if (sig_mutf8 == class_name) {
      found_class = loaded_classes[i];
    }
    if (sig_mutf8 != nullptr) {
      jvmti_->Deallocate((unsigned char*)sig_mutf8);
    }
  }

  if (found_class != nullptr) {
    jthread thread = nullptr;
    jvmti_->GetCurrentThread(&thread);

    // Class file load hooks are automatically managed on P (and newer) devices
    bool manually_toggle_load_hook =
        DeviceInfo::feature_level() < DeviceInfo::P;

    if (manually_toggle_load_hook) {
      CheckJvmtiError(
          jvmti_, jvmti_->SetEventNotificationMode(
                      JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }
    CheckJvmtiError(jvmti_, jvmti_->RetransformClasses(1, &found_class));
    if (manually_toggle_load_hook) {
      CheckJvmtiError(
          jvmti_, jvmti_->SetEventNotificationMode(
                      JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }

    if (thread != nullptr) {
      jni->DeleteLocalRef(thread);
    }
  }

  for (int i = 0; i < class_count; ++i) {
    jni->DeleteLocalRef(loaded_classes[i]);
  }
  jvmti_->Deallocate(reinterpret_cast<unsigned char*>(loaded_classes));
}

#endif

}  // namespace app_inspection