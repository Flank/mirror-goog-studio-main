package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "layout_inspector_view_java_proto-nojarjar",
    srcs = glob(["**/*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
)

android_java_proto_library(
    name = "layout_inspector_view_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
)
