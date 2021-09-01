package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "layout_inspector_view_java_proto-nojarjar",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

android_java_proto_library(
    name = "layout_inspector_view_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)
