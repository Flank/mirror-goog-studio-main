/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "agent/jvmti_helper.h"
#include "utils/log.h"

using layoutinspector::LambdaValue;
using layoutinspector::Property;
using layoutinspector::Property_Type;
using layoutinspector::PropertyEvent;
using layoutinspector::Resource;
using layoutinspector::StringEntry;
using profiler::Agent;
using profiler::CheckJvmtiError;
using profiler::CreateJvmtiEnv;
using profiler::GetThreadLocalJNI;
using profiler::JStringWrapper;
using profiler::Log;
using profiler::SetAllCapabilities;
using profiler::proto::AgentService;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::SendEventRequest;

/**
 * Native calls for loading the properties event protobuf.
 */
extern "C" {

#define DEBUG_ANALYZE_METHOD true

void saveResource(Resource *resource, jint namespace_, jint type, jint name) {
  resource->set_type(type);
  resource->set_namespace_(namespace_);
  resource->set_name(name);
}

Property *addProperty(jlong jevent, jlong jproperty) {
  if (jproperty != 0L) {
    Property *property = (Property *)jproperty;
    return property->add_element();
  } else {
    PropertyEvent *event = (PropertyEvent *)jevent;
    return event->add_property();
  }
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_allocatePropertyEvent(
    JNIEnv *env, jclass clazz) {
  PropertyEvent *event = new PropertyEvent();
  return (long)event;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_freePropertyEvent(
    JNIEnv *env, jclass clazz, jlong jevent) {
  if (jevent != 0L) {
    delete (PropertyEvent *)jevent;
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_sendPropertyEvent(
    JNIEnv *env, jclass clazz, jlong jevent, jlong viewId, jint generation) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  event->set_view_id((long)viewId);
  event->set_generation(generation);
  PropertyEvent property_event = *event;

  // Note: property_event is copied by value here which is not optimal.
  Agent::Instance().SubmitAgentTasks(
      {[property_event](AgentService::Stub &stub,
                        grpc::ClientContext &ctx) mutable {
        SendEventRequest request;
        auto *event = request.mutable_event();
        auto *inspector_event = event->mutable_layout_inspector_event();
        auto *properties = inspector_event->mutable_properties();
        *properties = property_event;
        event->set_is_ended(true);
        event->set_pid(getpid());
        event->set_kind(Event::LAYOUT_INSPECTOR);
        event->set_group_id(Event::PROPERTIES);
        EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addString(
    JNIEnv *env, jclass clazz, jlong jevent, jint id, jstring str) {
  JStringWrapper str_wrapper(env, str);

  PropertyEvent *event = (PropertyEvent *)jevent;
  StringEntry *string_entry = event->add_string();
  string_entry->set_id(id);
  string_entry->set_str(str_wrapper.get().c_str());
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addPropertySource(
    JNIEnv *env, jclass clazz, jlong jproperty, jint namespace_, jint type,
    jint name) {
  Property *property = (Property *)jproperty;
  saveResource(property->mutable_source(), namespace_, type, name);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addResolution(
    JNIEnv *env, jclass clazz, jlong jproperty, jint namespace_, jint type,
    jint name) {
  Property *property = (Property *)jproperty;
  saveResource(property->add_resolution_stack(), namespace_, type, name);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addIntProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jint value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int32_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addLongProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jlong value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int64_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addDoubleProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jdouble value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_double_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFloatProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jfloat value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_float_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addResourceProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jint resource_namespace, jint resource_type,
    jint resource_name) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  saveResource(property->mutable_resource_value(), resource_namespace,
               resource_type, resource_name);
  return (long)property;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addLayoutResource(
    JNIEnv *env, jclass clazz, jlong jevent, jint namespace_, jint type,
    jint name) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  saveResource(event->mutable_layout(), namespace_, type, name);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFlagProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  return (long)property;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFlagPropertyValue(
    JNIEnv *env, jclass clazz, jlong jproperty, jint flag) {
  Property *property = (Property *)jproperty;
  auto *flags = property->mutable_flag_value();
  flags->add_flag(flag);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addLambdaProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jint type, jint package_name, jint file_name, jint lambda_name,
    jint function_name, jint start_line, jint end_line) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(false);
  property->set_type(static_cast<Property_Type>(type));
  auto *lambda = property->mutable_lambda_value();
  lambda->set_package_name(package_name);
  lambda->set_file_name(file_name);
  lambda->set_lambda_name(lambda_name);
  lambda->set_function_name(function_name);
  lambda->set_start_line_number(start_line);
  lambda->set_end_line_number(end_line);
  return (long)property;
}

static jclass location_class = nullptr;
static jmethodID location_class_constructor = nullptr;

bool create_lambda_location_result_fields(JNIEnv *env) {
  static bool failure_creating_result = false;

  if (failure_creating_result) {
    return false;
  }
  if (location_class != nullptr) {
    return true;
  }
  failure_creating_result = true;  // In case the next lines throw exceptions...
  jclass local_location_class =
      env->FindClass("com/android/tools/agent/layoutinspector/LambdaLocation");
  location_class = (jclass)env->NewGlobalRef(local_location_class);
  location_class_constructor =
      env->GetMethodID(location_class, "<init>", "(Ljava/lang/String;II)V");
  if (location_class == nullptr || location_class_constructor == nullptr) {
    return false;
  }
  failure_creating_result = false;
  return true;
}

/**
 * Create a Jvmti environment.
 * The env is created and kept in a static for the duration of the JVM lifetime.
 * Check if we are able to get the can_get_line_numbers capability. If not just
 * return nullptr now and next time this is called.
 */
jvmtiEnv *getJvmti(JNIEnv *env) {
  static jvmtiEnv *jvmti_env = nullptr;
  static bool can_get_line_numbers = true;
  if (jvmti_env != nullptr || !can_get_line_numbers) {
    return jvmti_env;
  }
  can_get_line_numbers = false;
  JavaVM *vm;
  int error = env->GetJavaVM(&vm);
  if (error != 0) {
    Log::E(Log::Tag::LAYOUT_INSPECT,
           "Failed to get JavaVM instance for LayoutInspector with error "
           "code: %d",
           error);
    return nullptr;
  }
  // This will attach the current thread to the vm, otherwise
  // CreateJvmtiEnv(vm) below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);
  // Create a stand-alone jvmtiEnv to avoid any callback conflicts
  // with other profilers' agents.
  jvmti_env = CreateJvmtiEnv(vm);
  if (jvmti_env == nullptr) {
    Log::E(Log::Tag::LAYOUT_INSPECT,
           "Failed to initialize JVMTI env for LayoutInspector");
  } else {
    SetAllCapabilities(jvmti_env);
    jvmtiCapabilities capabilities;
    if (!CheckJvmtiError(jvmti_env,
                         jvmti_env->GetCapabilities(&capabilities))) {
      can_get_line_numbers = capabilities.can_get_line_numbers;
    }
    if (!can_get_line_numbers) {
      Log::E(Log::Tag::LAYOUT_INSPECT,
             "Failed to get the can_get_line_numbers capability for JVMTI");
      jvmti_env = nullptr;
    }
  }
  return jvmti_env;
}

/**
 * A range of instruction offsets that is known to originate from an inlined
 * function.
 */
typedef struct {
  jlocation start_location;
  jlocation end_location;
} InlineRange;

#ifdef DEBUG_ANALYZE_METHOD

void dumpMethod(int lineCount, jvmtiLineNumberEntry *lines, int variableCount,
                jvmtiLocalVariableEntry *variables, int rangeCount,
                InlineRange *ranges) {
  Log::D(Log::Tag::LAYOUT_INSPECT, "%s", "Analyze Method Lines");

  Log::D(Log::Tag::LAYOUT_INSPECT, "Local Variable table count=%d",
         variableCount);
  for (int i = 0; i < variableCount; i++) {
    jvmtiLocalVariableEntry *var = &variables[i];
    Log::D(Log::Tag::LAYOUT_INSPECT,
           "  %d: start=%lld, length=%d, name=%s, signature=%s, slot=%d", i,
           var->start_location, var->length, var->name, var->signature,
           var->slot);
  }

  Log::D(Log::Tag::LAYOUT_INSPECT, "Line Number table count=%d", lineCount);
  for (int i = 0; i < lineCount; i++) {
    jvmtiLineNumberEntry *line = &lines[i];
    Log::D(Log::Tag::LAYOUT_INSPECT, "  %d: start=%lld, line_number=%d", i,
           line->start_location, line->line_number);
  }

  Log::D(Log::Tag::LAYOUT_INSPECT, "Inline Ranges count=%d", rangeCount);
  for (int i = 0; i < rangeCount; i++) {
    InlineRange *range = &ranges[i];
    Log::D(Log::Tag::LAYOUT_INSPECT, "  %d: start=%lld, end=%lld", i,
           range->start_location, range->end_location);
  }
}
#endif

/**
 * Compute the ranges of inlined instructions from the local variables of a
 * method.
 *
 * The range_ptr must be freed with jvmtiEnv.Deallocate.
 */
void computeInlineRanges(jvmtiEnv *jvmti, int variableCount,
                         jvmtiLocalVariableEntry *variables,
                         int *rangeCount_ptr, InlineRange **ranges_ptr) {
  int count = 0;
  InlineRange *ranges = nullptr;
  for (int i = 0; i < variableCount; i++) {
    jvmtiLocalVariableEntry *variable = &variables[i];
    if (strncmp("$i$f$", variable->name, 5) == 0) {
      if (ranges == nullptr) {
        jvmti->Allocate(sizeof(InlineRange) * (variableCount - i),
                        (unsigned char **)&ranges);
      }
      ranges[count].start_location = variable->start_location;
      ranges[count].end_location = variable->start_location + variable->length;
      count++;
    }
  }
  *rangeCount_ptr = count;
  *ranges_ptr = ranges;
}

/**
 * Return true if a given line is from an inline function.
 * @param line to investigate
 * @param rangeCount is the number of known inline ranges
 * @param ranges the known inline ranges
 * @return true if the line offset is within one of thje inline ranges
 */
bool isInlined(jvmtiLineNumberEntry *line, int rangeCount,
               InlineRange *ranges) {
  for (int i = 0; i < rangeCount; i++) {
    InlineRange *range = &ranges[i];
    if (range->start_location <= line->start_location &&
        line->start_location < range->end_location) {
      return true;
    }
  }
  return false;
}

/**
 * Analyze the lines of a method to find start and end line excluding inlined
 * functions.
 * @param jvmti the JVMTI environment
 * @param lineCount number of lines found in the method
 * @param lines the actual lines
 * @param variableCount the number of entries of the local variables
 * @param variables the actual variables
 * @param start_line_ptr on return will hold the start line of this method or 0
 * if not found
 * @param end_line_ptr on return will hold the end line of this method or 0 if
 * not found
 * @return true if a method range is found
 */
bool analyzeLines(jvmtiEnv *jvmti, int lineCount, jvmtiLineNumberEntry *lines,
                  int variableCount, jvmtiLocalVariableEntry *variables,
                  int *start_line_ptr, int *end_line_ptr) {
  int rangeCount = 0;
  InlineRange *ranges = nullptr;
  computeInlineRanges(jvmti, variableCount, variables, &rangeCount, &ranges);
  int start_line = 0;
  int end_line = 0;

#ifdef DEBUG_ANALYZE_METHOD
  dumpMethod(lineCount, lines, variableCount, variables, rangeCount, ranges);
#endif

  for (int i = 0; i < lineCount; i++) {
    jvmtiLineNumberEntry *line = &lines[i];
    int line_number = line->line_number;
    if (line_number > 0 && !isInlined(line, rangeCount, ranges)) {
      if (start_line == 0) {
        start_line = line_number;
        end_line = line_number;
      } else if (line_number < start_line) {
        start_line = line_number;
      } else if (line_number > end_line) {
        end_line = line_number;
      }
    }
  }
  jvmti->Deallocate((unsigned char *)ranges);
  *start_line_ptr = start_line;
  *end_line_ptr = end_line;
  return start_line > 0;
}

/**
 * Deallocate the local variables and any allocations held by a local variable
 * entry
 * @param jvmti the JVMTI environment
 * @param variableCount the number of entries of the local variables
 * @param variables_ptr a reference to the actual variables
 */
void deallocateVariables(jvmtiEnv *jvmti, int variableCount,
                         jvmtiLocalVariableEntry **variables_ptr) {
  jvmtiLocalVariableEntry *variables = *variables_ptr;
  if (variables != nullptr) {
    for (int i = 0; i < variableCount; i++) {
      jvmtiLocalVariableEntry *entry = &variables[i];
      jvmti->Deallocate((unsigned char *)entry->name);
      jvmti->Deallocate((unsigned char *)entry->signature);
      jvmti->Deallocate((unsigned char *)entry->generic_signature);
    }
    jvmti->Deallocate((unsigned char *)variables);
  }
  *variables_ptr = nullptr;
}

void deallocateLines(jvmtiEnv *jvmti, jvmtiLineNumberEntry **lines_ptr) {
  jvmti->Deallocate((unsigned char *)*lines_ptr);
  *lines_ptr = nullptr;
}

const int ACC_BRIDGE = 0x40;

/**
 * Get the lambda source location.
 *
 * Use jvmti to get the source file name and the lines of the invoke
 * method of the generated class for a lambda. This class seem to have
 * 4 methods: 2 <clinit> and 2 invoke methods.
 * Only 1 of these methods has associated lines.
 *
 * Extract the start and end line from the first method that has a
 * line table and assume they are specified in ascending order.
 * If the line range is not found (in case of missing VM support)
 * just return without writing the line range.
 *
 * @param env the JNI environment
 * @param clazz the Properties class
 * @param lambda_class the compiler generated class of a lambda
 * @return a instance of LambdaLocation with source file and lines
 *         or null if information cannot be found.
 */
JNIEXPORT jobject JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_getLambdaLocation(
    JNIEnv *env, jclass clazz, jclass lambda_class) {
  if (!create_lambda_location_result_fields(env)) {
    return nullptr;
  }
  jvmtiEnv *jvmti = getJvmti(env);
  if (jvmti == nullptr) {
    return nullptr;
  }
  int methodCount;
  jmethodID *methods;
  jvmtiError error =
      jvmti->GetClassMethods(lambda_class, &methodCount, &methods);
  if (CheckJvmtiError(jvmti, error)) {
    return nullptr;
  }

  int variableCount = 0;
  jvmtiLocalVariableEntry *variables = nullptr;
  int lineCount = 0;
  jvmtiLineNumberEntry *lines = nullptr;
  int start_line = 0;
  int end_line = 0;

  for (int i = 0; i < methodCount; i++) {
    deallocateLines(jvmti, &lines);
    deallocateVariables(jvmti, variableCount, &variables);

    jmethodID methodId = methods[i];
    int modifiers = 0;
    error = jvmti->GetMethodModifiers(methodId, &modifiers);
    if (CheckJvmtiError(jvmti, error)) {
      break;
    }
    if ((modifiers & ACC_BRIDGE) != 0) {
      continue;  // Ignore bridge methods
    }

    char *name;
    error = jvmti->GetMethodName(methodId, &name, nullptr, nullptr);
    if (CheckJvmtiError(jvmti, error)) {
      break;
    }
    bool isInvokeMethod = strcmp(name, "invoke") == 0;
    Log::D(Log::Tag::LAYOUT_INSPECT, "Name: %s  isInvokeMethod: %d", name,
           isInvokeMethod);
    jvmti->Deallocate((unsigned char *)name);
    if (!isInvokeMethod) {
      continue;  // Ignore if the method name doesn't match "invoke"
    }

    error = jvmti->GetLocalVariableTable(methodId, &variableCount, &variables);
    if (CheckJvmtiError(jvmti, error)) {
      break;
    }
    error = jvmti->GetLineNumberTable(methodId, &lineCount, &lines);
    if (CheckJvmtiError(jvmti, error)) {
      break;
    }

    if (analyzeLines(jvmti, lineCount, lines, variableCount, variables,
                     &start_line, &end_line)) {
      break;
    }
  }
  deallocateLines(jvmti, &lines);
  deallocateVariables(jvmti, variableCount, &variables);
  jvmti->Deallocate((unsigned char *)methods);

  if (start_line <= 0) {
    return nullptr;
  }

  char *source_name_ptr;
  error = jvmti->GetSourceFileName(lambda_class, &source_name_ptr);
  if (CheckJvmtiError(jvmti, error)) {
    return nullptr;
  }
  jstring file_name = env->NewStringUTF(source_name_ptr);
  jvmti->Deallocate((unsigned char *)source_name_ptr);
  jobject result = env->NewObject(location_class, location_class_constructor,
                                  file_name, start_line, end_line);
  return result;
}
}
