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
#ifndef VARIABLE_REINIT_H
#define VARIABLE_REINIT_H

#include <string>
#include <vector>

#include "tools/base/deploy/agent/native/hotswap.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

struct ClassVarReInitWorkItem;

// When using JVMTI class redefinition, all newly introduced primitive
// variables are assigned with default value of their primitive types.
// The goal of this class is to initialize newly introduced static variables
// to their respective values.

// Not unlike all other service the agent perform before and after a swap,
// it first gathers state information before swap and perform restoration
// after the call to swap.
//
// In this case, this class will examine all the static variables a class has
// before the swap. By comparing the variable state list from the swap
// request, it can determine which static variables will be introduced.
//
// The list, stored as new_static_vars_, will be used to determine which
// variable will get to be initialized after swapping.
//
// For more detailed information go/ac-static-var-init

class VariableReinitializer {
 public:
  VariableReinitializer(bool var_reinit, jvmtiEnv* jvmti, JNIEnv* jni)
      : var_reinit(var_reinit), jvmti_(jvmti), jni_(jni) {}

  // Called before code swap for each class to be swapped.
  // This is the gathering step that populate the worklist.
  SwapResult::Status GatherPreviousState(jclass clz, const proto::ClassDef& def,
                                         std::string* error_msg);

  // Called after code swap. This initialize the variable in the work
  // list to their initial value.
  SwapResult::Status ReinitializeVariables(std::string* error_msg);

 private:
  int initialValuesAltered(jclass clz, const proto::ClassDef& def,
                           proto::ClassDef::FieldReInitState& state,
                           jfieldID fid, std::ostringstream& msg);

  // Trigger class initialization if the class is not already initialized. Class
  // initialization causes static variables to be initialized.
  bool TriggerClassInitialize(jclass clazz);

  bool var_reinit;
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
  std::vector<ClassVarReInitWorkItem*> new_static_vars_;
};

}  // namespace deploy
#endif
