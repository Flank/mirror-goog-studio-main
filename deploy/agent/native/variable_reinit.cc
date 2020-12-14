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
#include "tools/base/deploy/agent/native/variable_reinit.h"

#include <string.h>

#include <sstream>

#include "tools/base/deploy/agent/native/capabilities.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/log.h"

namespace {
template <class T>
class JvmtiAlloc {
  jvmtiEnv* env_;
  T* data_;

 public:
  JvmtiAlloc(jvmtiEnv* env) : env_(env), data_(0) {}

  ~JvmtiAlloc() { env_->Deallocate((unsigned char*)data_); }
  T** operator&() { return &data_; }

  T* operator*() { return data_; }
};
}  // namespace

namespace deploy {

// Stores information about a newly added static variablel
// It represent a class and a list of fields that it needs might need
// to be re-initialized.
//
// Example:
// package com.example;
// class foo { int x = 1; int y = 2; float z = 0.1l; }
//
// Would conceptually have entries like so:
// ---------------------------------------
// | com.example.foo |  [x, int  , 1   ] |
// |                 |  [y, int  , 2   ] |
// |                 |  [z, float, 0.1 ] |
// ---------------------------------------
//
// A list of all ClassVarReInitWorkItem item is stored in new_static_vars_.

typedef struct ClassVarReInitWorkItem {
  jclass klass;
  std::vector<proto::ClassDef::FieldReInitState>& states;
  ClassVarReInitWorkItem(jclass k,
                         std::vector<proto::ClassDef::FieldReInitState>& s)
      : klass(k), states(s) {}
} ClassVarReInitWorkItem;

namespace {
bool isPrimitive(const std::string& jvm_type) {
  return jvm_type == "B" || jvm_type == "C" || jvm_type == "I" ||
         jvm_type == "J" || jvm_type == "S" || jvm_type == "F" ||
         jvm_type == "D" || jvm_type == "Z";
}

bool isObject(const std::string& jvm_type) {
  return !jvm_type.empty() && jvm_type.at(0) == 'L';
}

bool isArray(const std::string& jvm_type) {
  return !jvm_type.empty() && jvm_type.at(0) == '[';
}

// Return a "best effort" guess error message should a
// newly introduced variable appears to be a Kotlin compiler
// generated lambda capture.
std::string GuessKotlinCaptureEror(const std::string& name) {
  // This is a valid JVM identifier and not a valid Java language
  // identifier which makes it safe to assume a compiler generated
  // this variable. Given the name, we can make an educated guess
  // that it is the compiler trying to capture a variable in a
  // lambda.
  const std::string& this_capture_prefix = "this$";

  if (name.find(this_capture_prefix) != std::string::npos) {
    return "\nPossible new lambda capture of an outer \"this\" variable";
  }

  return "";
}

}  // namespace

SwapResult::Status VariableReinitializer::GatherPreviousState(
    jclass clz, const proto::ClassDef& def, std::string* error_msg) {
  // Create the entry. If there is nothing to do,
  // we just don't add to the work list later.
  auto newVars = new std::vector<proto::ClassDef::FieldReInitState>();
  ClassVarReInitWorkItem* work_item = new ClassVarReInitWorkItem(clz, *newVars);

  // For keeping track of number of R.java ID changed.
  std::ostringstream r_class_errors;
  int num_r_field_modified = 0;

  // Next step is to go though all the fields in the new definition of
  // the class and attempt to search for the same field. If we cannot find it,
  // it will be a newly added field that require initialization.
  int new_field_count = def.fields_size();
  for (int j = 0; j < new_field_count; j++) {
    proto::ClassDef::FieldReInitState state = def.fields(j);
    jint field_count;
    JvmtiAlloc<jfieldID> fields(jvmti_);
    jvmti_->GetClassFields(clz, &field_count, &fields);

    bool found = false;
    for (jint i = 0; i < field_count; i++) {
      JvmtiAlloc<char> name(jvmti_);
      JvmtiAlloc<char> signature(jvmti_);
      JvmtiAlloc<char> generic(jvmti_);
      jvmti_->GetFieldName(clz, (*fields)[i], &name, &signature, &generic);

      if (state.name() == *name) {
        const std::string& r_class_prefix = ".R$";

        if (def.name().find(r_class_prefix) != std::string::npos) {
          // R classes are not initialized unless the application uses
          // reflection. We need to ask the class to initialize in order to
          // ensure we have the correct "before" values for all fields.
          if (!TriggerClassInitialize(clz)) {
            Log::W(
                "Could not trigger initialize for class '%s'; if it was not "
                "already initialized, stable-id errors may occur",
                def.name().c_str());
          }
          num_r_field_modified += initialValuesAltered(
              clz, def, state, (*fields)[i], r_class_errors);
        }
        found = true;
        break;
      }
    }

    if (!found) {
      // Non-statics. All of them not supported right now.
      if (!state.staticvar()) {
        std::ostringstream msg;
        msg << "Adding non-static variable " << state.name()
            << " is not currently supported" << std::endl;
        *error_msg = msg.str();
        if (isPrimitive(state.type())) {
          std::ostringstream msg;
          msg << "Adding field primitive " << def.name() << "." << state.name()
              << std::endl;
          *error_msg = msg.str();
          return SwapResult::UNSUPPORTED_REINIT_NON_STATIC_PRIMITIVE;
        } else if (isArray(state.type())) {
          std::ostringstream msg;
          msg << "Adding field array " << def.name() << "." << state.name()
              << std::endl;
          *error_msg = msg.str();
          return SwapResult::UNSUPPORTED_REINIT_NON_STATIC_ARRAY;
        } else if (isObject(state.type())) {
          std::ostringstream msg;
          msg << "Adding field object " << def.name() << "." << state.name()
              << GuessKotlinCaptureEror(state.name()) << std::endl;
          *error_msg = msg.str();
          return SwapResult::UNSUPPORTED_REINIT_NON_STATIC_OBJECT;
        } else {
          return SwapResult::UNSUPPORTED_REINIT;  // should not be reachable.
        }

        // Statics.
      } else {
        // Object types are not suppoeted.
        if (isArray(state.type())) {
          std::ostringstream msg;
          msg << "Adding static array " << def.name() << "." << state.name()
              << std::endl;
          *error_msg = msg.str();
          return SwapResult::UNSUPPORTED_REINIT_STATIC_ARRAY;
        } else if (isObject(state.type())) {
          std::ostringstream msg;
          msg << "Adding static object " << def.name() << "." << state.name()
              << std::endl;
          *error_msg = msg.str();
          return SwapResult::UNSUPPORTED_REINIT_STATIC_OBJECT;

        } else if (isPrimitive(state.type())) {
          if (!this->var_reinit) {
            std::ostringstream msg;
            msg << "Adding static primitive " << def.name() << "."
                << state.name() << std::endl;
            *error_msg = msg.str();
            return SwapResult::UNSUPPORTED_REINIT_STATIC_PRIMITIVE;
          }

          // Primitives. Supported should they be compile time constants.
          if (state.state() != proto::ClassDef::FieldReInitState::CONSTANT) {
            std::ostringstream msg;
            msg << "Adding static primitive " << def.name() << "."
                << state.name() << " not known to be compile time constant"
                << std::endl;
            *error_msg = msg.str();
            return SwapResult::UNSUPPORTED_REINIT_STATIC_PRIMITIVE_NOT_CONSTANT;
          }
        } else {
          *error_msg = "unknown error";
          return SwapResult::UNSUPPORTED_REINIT;  // should not be reachable.
        }
      }
      newVars->emplace_back(state);
    }
  }

  if (!newVars->empty()) {
    new_static_vars_.emplace_back(work_item);
  }

  if (num_r_field_modified > 0) {
    if (num_r_field_modified > 5) {
      std::ostringstream msg;
      msg << "Total of " << num_r_field_modified
          << " R.class ID values have been modified."
          << " Possible unstable ID generation between previous build."
          << std::endl;
      *error_msg = msg.str();
    } else {
      *error_msg = r_class_errors.str();
    }
    return SwapResult::UNSUPPORTED_REINIT_R_CLASS_VALUE_MODIFIED;
  }
  return SwapResult::SUCCESS;
}

SwapResult::Status VariableReinitializer::ReinitializeVariables(
    std::string* error_msg) {
  for (auto& work_item : new_static_vars_) {
    jclass& cls = work_item->klass;
    std::vector<proto::ClassDef::FieldReInitState>& vars = work_item->states;
    for (auto var_it = vars.begin(); var_it != vars.end(); var_it++) {
      proto::ClassDef::FieldReInitState var = *var_it;

      if (var.type() == "B") {
        jclass byte_class = jni_->FindClass("java/lang/Byte");
        jmethodID parse_byte = jni_->GetStaticMethodID(byte_class, "parseByte",
                                                       "(Ljava/lang/String;)B");
        jbyte result = jni_->CallStaticByteMethod(
            byte_class, parse_byte, jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticByteField(cls, fid, result);
      } else if (var.type() == "C") {
        jclass char_class = jni_->FindClass("java/lang/String");
        jmethodID parse_char = jni_->GetMethodID(char_class, "charAt", "(I)C");
        jchar result = jni_->CallCharMethod(
            jni_->NewStringUTF(var.value().c_str()), parse_char, 0);
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticCharField(cls, fid, result);
      } else if (var.type() == "I") {
        jclass int_class = jni_->FindClass("java/lang/Integer");
        jmethodID parse_int = jni_->GetStaticMethodID(int_class, "parseInt",
                                                      "(Ljava/lang/String;)I");
        jint result = jni_->CallStaticIntMethod(
            int_class, parse_int, jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticIntField(cls, fid, result);
      } else if (var.type() == "J") {
        jclass long_class = jni_->FindClass("java/lang/Long");
        jmethodID parse_long = jni_->GetStaticMethodID(long_class, "parseLong",
                                                       "(Ljava/lang/String;)J");
        jlong result = jni_->CallStaticLongMethod(
            long_class, parse_long, jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticLongField(cls, fid, result);
      } else if (var.type() == "S") {
        jclass short_class = jni_->FindClass("java/lang/Short");
        jmethodID parse_short = jni_->GetStaticMethodID(
            short_class, "parseShort", "(Ljava/lang/String;)S");
        jshort result = jni_->CallStaticShortMethod(
            short_class, parse_short, jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticShortField(cls, fid, result);
      } else if (var.type() == "F") {
        jclass float_class = jni_->FindClass("java/lang/Float");
        jmethodID parse_float = jni_->GetStaticMethodID(
            float_class, "parseFloat", "(Ljava/lang/String;)F");
        jfloat result = jni_->CallStaticFloatMethod(
            float_class, parse_float, jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticFloatField(cls, fid, result);
      } else if (var.type() == "D") {
        jclass double_class = jni_->FindClass("java/lang/Double");
        jmethodID parse_double = jni_->GetStaticMethodID(
            double_class, "parseDouble", "(Ljava/lang/String;)D");
        jdouble result = jni_->CallStaticDoubleMethod(
            double_class, parse_double,
            jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticDoubleField(cls, fid, result);
      } else if (var.type() == "Z") {
        jclass boolean_class = jni_->FindClass("java/lang/Boolean");
        jmethodID parse_boolean = jni_->GetStaticMethodID(
            boolean_class, "parseBoolean", "(Ljava/lang/String;)Z");
        jboolean result = jni_->CallStaticBooleanMethod(
            boolean_class, parse_boolean,
            jni_->NewStringUTF(var.value().c_str()));
        jfieldID fid =
            jni_->GetStaticFieldID(cls, var.name().c_str(), var.type().c_str());
        jni_->SetStaticBooleanField(cls, fid, result);
      } else {
        *error_msg = "unknown error";
        return SwapResult::UNSUPPORTED_REINIT;  // should not be reachable.
      }
    }

    delete &vars;
    delete work_item;
  }

  return SwapResult::SUCCESS;
}

int VariableReinitializer::initialValuesAltered(
    jclass clz, const proto::ClassDef& def,
    proto::ClassDef::FieldReInitState& state, jfieldID fid,
    std::ostringstream& msg) {
  int total = 0;
  jclass int_class = jni_->FindClass("java/lang/Integer");
  jmethodID parse_int =
      jni_->GetStaticMethodID(int_class, "parseInt", "(Ljava/lang/String;)I");
  jint new_value = jni_->CallStaticIntMethod(
      int_class, parse_int, jni_->NewStringUTF(state.value().c_str()));
  jint cur_value = jni_->GetStaticIntField(clz, fid);

  if (new_value != cur_value) {
    msg << def.name() << "." << state.name() << "changed from" << cur_value
        << " to " << new_value << std::endl;
    total++;
  }
  return total;
}

bool VariableReinitializer::TriggerClassInitialize(jclass clazz) {
  char* class_sig;
  if (jvmti_->GetClassSignature(clazz, &class_sig, nullptr) !=
      JVMTI_ERROR_NONE) {
    return false;
  }

  std::string class_name(class_sig + 1);     // Trim the L
  class_name.resize(class_name.size() - 1);  // Trim the ;
  std::replace(class_name.begin(), class_name.end(), '/', '.');

  jobject class_loader;
  if (jvmti_->GetClassLoader(clazz, &class_loader) != JVMTI_ERROR_NONE) {
    return false;
  }

  JniClass java_class(jni_, "java/lang/Class");

  // The reflective method Class#forName() can be used to initialize the class.
  // The second parameter determines if the forName method tries to initialize
  // the class.
  java_class.CallStaticObjectMethod(
      "forName",
      "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;",
      jni_->NewStringUTF(class_name.c_str()), true, class_loader);
  jvmti_->Deallocate((unsigned char*)class_sig);
  if (jni_->ExceptionCheck()) {
    jni_->ExceptionDescribe();
    jni_->ExceptionClear();
    return false;
  }
  return true;
}

}  // namespace deploy
