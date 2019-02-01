#ifndef LAYOUT_INSPECTOR_AGENT_COMMAND_H_
#define LAYOUT_INSPECTOR_AGENT_COMMAND_H_

#include "agent/agent.h"

using profiler::Agent;
using profiler::proto::Command;

class LayoutInspectorAgentCommand {
 public:
  static void RegisterAgentLayoutInspectorCommandHandler(JavaVM* vm) {
    // Register command handlers for agent based commands.
    Agent::Instance().RegisterCommandHandler(
        Command::LAYOUT_INSPECTOR, [vm](const Command* command) -> void {
          JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
          jclass inspector_class = jni_env->FindClass(
              "com/android/tools/agent/layoutinspector/LayoutInspectorService");
          // Grab our static instance method.
          jmethodID instance_method = jni_env->GetStaticMethodID(
              inspector_class, "instance",
              "()Lcom/android/tools/agent/layoutinspector/"
              "LayoutInspectorService;");
          jobject inspector_service =
              jni_env->CallStaticObjectMethod(inspector_class, instance_method);
          jmethodID start_command_method = jni_env->GetMethodID(
              inspector_class, "onStartLayoutInspectorCommand", "()V");
          jni_env->CallVoidMethod(inspector_service, start_command_method);
        });
  }
};
#endif  // LAYOUT_INSPECTOR_AGENT_COMMAND_H_
