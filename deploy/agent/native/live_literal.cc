/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include "tools/base/deploy/agent/native/live_literal.h"

#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/agent/native/recompose.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/sites/sites.h"

#define ENABLE_HELPER_CLASS_STARTED 0
#define ENABLE_HELPER_CLASS_UNCHANGED 1
#define ENABLE_HELPER_CLASS_FAILED 2

namespace deploy {

const char* LiveLiteral::kSupportClass =
    "com/android/tools/deploy/instrument/LiveLiteralSupport";

namespace {

// Keep a list of all instrumented live literal helpers
// so we no longer need to instrument it in this section.
std::unordered_set<std::string> instrumented_helpers;
std::string applicationId;

// Note that this is a callback we pass into JVMTI. It is likely
// that our agent already exited upon invocation. Therefore,
// we will not be passing any failture message to the installer
// from here.
extern "C" void JNICALL Agent_LiveLiteralHelperClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass klass, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  dex::Reader old_reader(class_data, class_data_len);
  std::string vmTypeName = "L"_s + name + ";";
  auto class_index = old_reader.FindClassIndex(vmTypeName.c_str());
  old_reader.CreateClassIr(class_index);
  auto old_ir = old_reader.GetIr();
  slicer::MethodInstrumenter mi(old_ir);
  mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
      "Lcom/android/tools/deploy/instrument/LiveLiteralSupport;", "reinit"));
  if (!mi.InstrumentMethod(
          ir::MethodId(vmTypeName.c_str(), "<clinit>", "()V"))) {
    Log::E("Count not instrument helper: %s", name);
  }

  dex::Writer writer(old_ir);
  JvmtiAllocator allocator(jvmti);
  size_t new_size;
  unsigned char* result = writer.CreateImage(&allocator, &new_size);

  const std::string overlay_dir = Sites::AppOverlays(applicationId);
  if (!IO::mkpath(overlay_dir.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) {
    if (errno != EEXIST) {
      Log::E("Could not create %s", overlay_dir.c_str());
    }
  }

  std::string ll_dir = Sites::AppLiveLiteral(applicationId);
  if (!IO::mkpath(ll_dir.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) {
    if (errno != EEXIST) {
      Log::E("Could not create %s", ll_dir.c_str());
    }
  }

  std::string filename = name;
  std::replace(filename.begin(), filename.end(), '/', '.');
  std::string ll_helper_file = ll_dir + filename + ".dex";

  // We don't to write the patched file if it already exists.
  if (!IO::access(ll_helper_file, F_OK)) {
    return;
  }

  // TODO: For better performance, we should move this write to the
  // the Java worker thread. It knows what needs to be patched
  // and it does it in a worker thread in the background.
  int fd = IO::creat(ll_helper_file.c_str(), S_IRUSR | S_IWUSR);
  if (fd == -1) {
    Log::E("Could not create %s", ll_dir.c_str());
  }
  write(fd, result, new_size);
  close(fd);
}

}  // namespace

jstring LiveLiteral::LookUpKeyByOffSet(const std::string& helper, int offset) {
  // Java:
  // Method[] results = Class.forName(helper).getDeclaredMethods();
  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(), helper);

  if (klass == nullptr) {
    jni_->ExceptionClear();
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("Cannot find Live Literal helper class: + helper");
    return nullptr;
  }

  jmethodID get_all_methods =
      jni_->GetMethodID(jni_->FindClass("java/lang/Class"),
                        "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");

  if (get_all_methods == nullptr) {
    // Almost impossible.
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("java.lang.Class.getDeclaredMethods does not exists");
    return nullptr;
  }

  // This can't be null but it could be empty.
  jobjectArray result =
      (jobjectArray)jni_->CallObjectMethod(klass, get_all_methods);
  jsize length = jni_->GetArrayLength(result);

  // Java:
  // for (Method func : result) {
  //   LiveLiteralInfo annotation = func.getAnnotation(LiveLiteralInfo.class);
  //   if (annotation != null) {
  //     ...
  //   }
  // }
  jclass info_class = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(),
      "androidx/compose/runtime/internal/LiveLiteralInfo");

  if (info_class == nullptr) {
    jni_->ExceptionClear();
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("Cannot find Live LiteralInfo class");
    return nullptr;
  }

  jclass method_class = jni_->FindClass("java/lang/reflect/Method");
  if (method_class == nullptr) {
    // Almost impossible.
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("java.lang.reflect.Method does not exists");
    return nullptr;
  }

  jmethodID get_annotation =
      jni_->GetMethodID(method_class, "getAnnotation",
                        "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;");

  if (get_annotation == nullptr) {
    // Almost impossible.
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra(
        "java.lang.reflect.Method.getAnnotation() does not exists");
    return nullptr;
  }

  jmethodID get_key =
      jni_->GetMethodID(info_class, "key", "()Ljava/lang/String;");
  if (get_key == nullptr) {
    // key() should be in the Compose API. Most likely we are out of sync
    // with the Compose compiler.
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("LiveLiteralInfo.key() does not exists");
    return nullptr;
  }

  jmethodID get_offset = jni_->GetMethodID(info_class, "offset", "()I");
  if (get_offset == nullptr) {
    // offset() should be in the Compose API. Most likely we are out of sync
    // with the Compose compiler.
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("LiveLiteralInfo.offset() does not exists");
    return nullptr;
  }

  // Return any key that matches the given offset.
  for (int i = 0; i < length; i++) {
    jobject func = jni_->GetObjectArrayElement(result, i);
    jobject annotation =
        jni_->CallObjectMethod(func, get_annotation, info_class);

    if (annotation != nullptr) {
      jstring key = (jstring)jni_->CallObjectMethod(annotation, get_key);
      jint cur_offset = jni_->CallIntMethod(annotation, get_offset);
      if (cur_offset == offset) {
        return key;
      }
    }
  }

  return nullptr;
}

proto::AgentLiveLiteralUpdateResponse LiveLiteral::Update(
    const proto::LiveLiteralUpdateRequest& request) {
  // Set to OK until first sign of an error.
  response_.set_status(proto::AgentLiveLiteralUpdateResponse::OK);
  applicationId = package_name_;

  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(),
      "androidx/compose/runtime/internal/LiveLiteralKt");
  if (klass == nullptr) {
    jni_->ExceptionClear();
    response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
    response_.set_extra("LiveLiteralKt Not found!");
    return response_;
  }

  if (!InstrumentApplication(jvmti_, jni_, request.package_name(),
                             /*overlay_swap*/ false)) {
    response_.set_status(
        proto::AgentLiveLiteralUpdateResponse::INSTRUMENTATION_FAILED);
    ErrEvent("Could not instrument application");
    return response_;
  }

  bool needs_recompose = false;
  jint local_enable = 2;
  JniClass support(jni_, LiveLiteral::kSupportClass);
  jobject package_name = jni_->NewStringUTF(package_name_.c_str());

  // From Beta07 and onward, each helper will hold the enabled boolean to
  // toggle live literal update readiness.
  for (auto update : request.updates()) {
    const std::string key = update.key();
    if (!key.empty()) {
      continue;
    }
    jclass helper = class_finder_.FindInClassLoader(
        class_finder_.GetApplicationClassLoader(), update.helper_class());
    if (helper == nullptr) {
      jni_->ExceptionClear();
      response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
      std::stringstream stream;
      stream << "Helper " << helper << " not found!";
      response_.set_extra(stream.str());
      return response_;
    }
    local_enable = support.CallStaticIntMethod(
        "enableHelperClass", "(Ljava/lang/Class;Ljava/lang/String;)I", helper,
        package_name);
  }

  if (local_enable == ENABLE_HELPER_CLASS_STARTED) {
    // Successfully enabled from a disabled state.
    needs_recompose = true;
  } else if (local_enable == ENABLE_HELPER_CLASS_UNCHANGED) {
    // Successfully enabled from already enabled state.
    needs_recompose = false;
  } else if (local_enable == ENABLE_HELPER_CLASS_FAILED) {
    // No local flag detected. Attempt to enable the older global flag.
    needs_recompose = support.CallStaticBooleanMethod(
        "enableGlobal", "(Ljava/lang/Class;Ljava/lang/String;)Z", klass,
        package_name);
  }

  if (needs_recompose) {
    Recompose recompose(jvmti_, jni_);
    jobject reloader = recompose.GetComposeHotReload();
    if (reloader == nullptr) {
      ErrEvent("GetComposeHotReload was not found.");
    }
    jobject state = recompose.SaveStateAndDispose(reloader);
    recompose.LoadStateAndCompose(reloader, state);
  }

  JniClass live_literal_kt(jni_, klass);

  for (auto update : request.updates()) {
    const std::string key = update.key();
    jobject jkey;
    if (key.empty()) {
      const std::string helper = update.helper_class();
      int offset = update.offset();
      jkey = LookUpKeyByOffSet(helper, offset);
      if (jkey == nullptr) {
        response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
        std::stringstream stream;
        stream << "Helper " << helper << " with offset " << std::hex << offset
               << " not found!";
        response_.set_extra(stream.str());
        return response_;
      }
    } else {
      jkey = jni_->NewStringUTF(key.c_str());
    }

    std::string ins_error = InstrumentHelper(update.helper_class());
    if (!ins_error.empty()) {
      response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
      response_.set_extra(ins_error);
      return response_;
    }

    // TODO: Make the call to updateLiveLiteralValue() part of add()
    // That way there is only one function call here and all the logic will
    // will just be written in Java instead of half in the hard-to-read JNI
    // code.
    jobject value = nullptr;

    if (update.type() == "Ljava/lang/String;") {
      LogEvent("Live Literal Update with String");
      value = jni_->NewStringUTF(update.value().c_str());
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);

    } else if (update.type() == "C") {
      LogEvent("Live Literal Update with Character");
      jclass string_class = jni_->FindClass("java/lang/String");
      jmethodID parse_char = jni_->GetMethodID(string_class, "charAt", "(I)C");
      jchar result = jni_->CallCharMethod(
          jni_->NewStringUTF(update.value().c_str()), parse_char, 0);

      jclass char_class = jni_->FindClass("java/lang/Character");
      jmethodID parse = jni_->GetStaticMethodID(char_class, "valueOf",
                                                "(C)Ljava/lang/Character;");
      value = jni_->CallStaticObjectMethod(char_class, parse, result);
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);

    } else if (update.type() == "B") {
      LogEvent("Live Literal Update with Byte");
      jclass byte_class = jni_->FindClass("java/lang/Byte");
      jmethodID parse = jni_->GetStaticMethodID(
          byte_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Byte;");
      value = jni_->CallStaticObjectMethod(
          byte_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "I") {
      LogEvent("Live Literal Update with Integer");
      jclass int_class = jni_->FindClass("java/lang/Integer");
      jmethodID parse = jni_->GetStaticMethodID(
          int_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Integer;");
      value = jni_->CallStaticObjectMethod(
          int_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "J") {
      LogEvent("Live Literal Update with Long");
      jclass long_class = jni_->FindClass("java/lang/Long");
      jmethodID parse = jni_->GetStaticMethodID(
          long_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Long;");
      value = jni_->CallStaticObjectMethod(
          long_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "S") {
      LogEvent("Live Literal Update with Short");
      jclass short_class = jni_->FindClass("java/lang/Short");
      jmethodID parse = jni_->GetStaticMethodID(
          short_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Short;");
      value = jni_->CallStaticObjectMethod(
          short_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "F") {
      LogEvent("Live Literal Update with Float");
      jclass float_class = jni_->FindClass("java/lang/Float");
      jmethodID parse = jni_->GetStaticMethodID(
          float_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Float;");
      value = jni_->CallStaticObjectMethod(
          float_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "D") {
      LogEvent("Live Literal Update with Double");
      jclass double_class = jni_->FindClass("java/lang/Double");
      jmethodID parse = jni_->GetStaticMethodID(
          double_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Double;");
      value = jni_->CallStaticObjectMethod(
          double_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else if (update.type() == "Z") {
      LogEvent("Live Literal Update with Boolean");
      jclass bool_class = jni_->FindClass("java/lang/Boolean");
      jmethodID parse = jni_->GetStaticMethodID(
          bool_class, "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;");
      value = jni_->CallStaticObjectMethod(
          bool_class, parse, jni_->NewStringUTF(update.value().c_str()));
      live_literal_kt.CallStaticVoidMethod(
          "updateLiveLiteralValue", "(Ljava/lang/String;Ljava/lang/Object;)V",
          jkey, value);
    } else {
      response_.set_status(proto::AgentLiveLiteralUpdateResponse::ERROR);
      response_.set_extra("Live Literal Update with Unknown Type: " +
                          update.type());
    }
    jstring helper_name = jni_->NewStringUTF(update.helper_class().c_str());
    support.CallStaticVoidMethod(
        "add", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
        helper_name, jkey, value);
  }
  return response_;
}

std::string LiveLiteral::InstrumentHelper(const std::string& helper) {
  // First check if we already instrumented this at least once.
  if (instrumented_helpers.find(helper) != instrumented_helpers.end()) {
    return "";
  }
  instrumented_helpers.insert(helper);

  // Note that this function is thread safe.
  //
  // Because caller of InstrumentHelper will always be the main thread of
  // the application, there will always be one thread executing this method.
  //
  // Moreover, because a single helper will only be instrumented once,
  // each callback will always be instrumenting a DIFFERENT helper, making
  // each callback thread safe since they don't read / write on the same data.
  jclass klass = class_finder_.FindInClassLoader(
      class_finder_.GetApplicationClassLoader(), helper);

  if (klass == nullptr) {
    jni_->ExceptionClear();
    return "Live Literal Helper " + helper + " not found";
  }

  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_LiveLiteralHelperClassFileLoadHook;

  CheckJvmti(
      jvmti_->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)),
      "Error setting event callbacks for live literal helper instrumentation");

  CheckJvmti(jvmti_->SetEventNotificationMode(
                 JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not enable class file load hook event for live literal "
             "helper instrumentation");

  // We invoke retransformation to pull the dex file out of the VM for
  // instrumentation. After that we are just going to push the result to the
  // overlay directory and not do any redefinition in the VM.
  CheckJvmti(
      jvmti_->RetransformClasses(1, &klass),
      "Could not retransform classes for live literal helper instrumentation");

  CheckJvmti(jvmti_->SetEventNotificationMode(
                 JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not disable class file load hook event after live literal "
             "helper instrumentation");

  // We do this to clean up after ourselves. Not a huge deal if this fails.
  CheckJvmti(jvmti_->SetEventCallbacks(nullptr, 0),
             "Error clearing event callbacks after live literal helper "
             "instrumentation");

  return "";
}

}  // namespace deploy
