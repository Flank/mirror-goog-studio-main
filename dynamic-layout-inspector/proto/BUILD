package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "cc_grpc_proto_library")

android_java_proto_library(
    name = "java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.0.3",
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = ["skia.proto"],
    grpc_support = 1,
    includes = ["."],
)
