cmake_minimum_required(VERSION 3.15)
project(agent)
add_subdirectory("${PROJECT_SOURCE_DIR}/../../../test/native/testing" "${PROJECT_BINARY_DIR}/../../../test/native/testing")
set(CMAKE_CXX_STANDARD 14)

include_directories(commands)
include_directories("../../../../../../../external/grpc-grpc/include")
include_directories("../../../../../../../external/protobuf/src")
include_directories("../../../../../../../bazel-bin/tools/base/transport")
include_directories("../../../../../transport/native")
include_directories("../../../../../../../prebuilts/studio/jdk/linux/include")
include_directories("../../../../../../../prebuilts/studio/jdk/linux/include/linux")

add_executable(agent
        commands/layoutinspector_agent_command.cc
        commands/layoutinspector_agent_command.h
        component_tree_java_jni.cc
        layoutinspector_java_jni.cc
        properties_java_jni.cc
        resource_configuration_java_jni.cc)
