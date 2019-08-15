#ifndef LAYOUT_INSPECTOR_AGENT_COMMAND_H_
#define LAYOUT_INSPECTOR_AGENT_COMMAND_H_

#include "agent/agent.h"

using layoutinspector::EditProperty;
using layoutinspector::LayoutInspectorCommand;
using layoutinspector::LayoutInspectorCommand_Type;
using layoutinspector::PropertyEvent;
using profiler::Agent;
using profiler::proto::Command;
using profiler::proto::SendEventRequest;

class LayoutInspectorAgentCommand {
 public:
  static void RegisterAgentLayoutInspectorCommandHandler(JavaVM* vm) {
    // Register command handlers for agent based commands.
    Agent::Instance().RegisterCommandHandler(
        Command::LAYOUT_INSPECTOR, [vm](const Command* command) -> void {
          const LayoutInspectorCommand* liCommand =
              &command->layout_inspector();

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

          switch (liCommand->type()) {
            case LayoutInspectorCommand::GET_PROPERTIES: {
              PropertyEvent event;
              long viewId = liCommand->view_id();
              jvalue args[] = {jvalue{.j = viewId}, jvalue{.j = (long)&event}};
              jmethodID properties_command_method = jni_env->GetMethodID(
                  inspector_class, "onGetPropertiesInspectorCommand", "(JJ)V");
              jni_env->CallVoidMethodA(inspector_service,
                                       properties_command_method, args);
              break;
            }

            case LayoutInspectorCommand::STOP: {
              jmethodID stop_command_method = jni_env->GetMethodID(
                  inspector_class, "onStopLayoutInspectorCommand", "()V");
              jni_env->CallVoidMethod(inspector_service, stop_command_method);
              break;
            }

            case LayoutInspectorCommand::START: {
              jmethodID start_command_method = jni_env->GetMethodID(
                  inspector_class, "onStartLayoutInspectorCommand", "()V");
              jni_env->CallVoidMethod(inspector_service, start_command_method);
              break;
            }

            case LayoutInspectorCommand::EDIT_PROPERTY: {
              const EditProperty* edit_command = &liCommand->edit_property();

              jmethodID edit_property_command_method = jni_env->GetMethodID(
                  inspector_class, "onEditPropertyInspectorCommand", "(JII)V");

              jni_env->CallVoidMethod(
                  inspector_service, edit_property_command_method,
                  liCommand->view_id(), edit_command->attribute_id(),
                  edit_command->int32_value());
              break;
            }

            default:
              // Ignore unknown commands
              break;
          }
        });
  }
};
#endif  // LAYOUT_INSPECTOR_AGENT_COMMAND_H_
