package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "cc_grpc_proto_library")

android_java_proto_library(
    name = "transport_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    include_prefix = "proto",
    tags = ["no_windows"],
)
