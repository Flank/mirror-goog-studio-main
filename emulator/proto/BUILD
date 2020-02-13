load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "emulator_java_proto",
    srcs = [
        "emulator_controller.proto",
        "snapshot.proto",
        "snapshot-service.proto",
    ],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.0.3",
    visibility = ["//visibility:public"],
)
