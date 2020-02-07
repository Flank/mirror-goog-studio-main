cmake_minimum_required(VERSION 3.15)
project(testing)

set(CMAKE_CXX_STANDARD 14)

include_directories("../../../../../../../bazel-bin/tools/base/transport")
include_directories("../../../../../transport/native")
include_directories("../../../../../transport/test-framework/src/main/native/testing")
include_directories("../../../../../../../prebuilts/studio/jdk/linux/include")
include_directories("../../../../../../../prebuilts/studio/jdk/linux/include/linux")

add_executable(testing
    component_tree_test_java_jni.cc
    properties_test_java_jni.cc)
