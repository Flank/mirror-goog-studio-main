# just for use by clion, not for actually building

cmake_minimum_required(VERSION 3.13)
project(skia)

set(CMAKE_CXX_STANDARD 14)

include_directories(.)
include_directories("../../../../../../../external/skia/include/core")
include_directories("../../../../../../../external/skia/include/utils")
include_directories("../../../../../../../external/grpc-grpc/include")
include_directories("../../../../../../../external/protobuf/src")
include_directories("../../../../../../../bazel-genfiles/tools/base/dynamic-layout-inspector/proto")

add_executable(skia
        server.cc
        tree_building_canvas.cc tree_building_canvas.h)