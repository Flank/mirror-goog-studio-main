#ifndef ECHO_AGENT_COMMAND_H_
#define ECHO_AGENT_COMMAND_H_

#include "agent/agent.h"

using profiler::Agent;
using profiler::proto::Command;

class EchoAgentCommand {
 public:
  static void RegisterAgentEchoCommandHandler(JavaVM* vm) {
    // Register command handlers for agent based commands.
    Agent::Instance().RegisterCommandHandler(
        Command::ECHO, [vm](const Command* command) -> void {
          JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
          // Grab a java Class object to represent our echo class.
          jclass echo_class =
              jni_env->FindClass("com/android/tools/agent/echo/EchoService");
          // Grab our static instance method.
          jmethodID instance_method = jni_env->GetStaticMethodID(
              echo_class, "Instance",
              "()Lcom/android/tools/agent/echo/EchoService;");
          // Call it to grab a pointer to our echo service.
          jobject echo_service =
              jni_env->CallStaticObjectMethod(echo_class, instance_method);
          // Grab a handle to our echo command method.
          jmethodID echo_command_method = jni_env->GetMethodID(
              echo_class, "onEchoCommand", "(Ljava/lang/String;)V");
          // Call it with our command arguments.
          jstring message =
              jni_env->NewStringUTF(command->echo_data().data().c_str());
          jni_env->CallVoidMethod(echo_service, echo_command_method, message);
          jni_env->DeleteLocalRef(message);
        });
  }
};
#endif  // ECHO_AGENT_COMMAND_H_