load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

java_proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    pom = ":pom",
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "proto",
    group = "com.android.tools.emulator",
    source = "//tools/buildSrc/base:base_version",
)

android_java_proto_library(
    name = "emulator_java_proto",
    srcs = [
        "emulator_controller.proto",
        "rtc_service.proto",
        "snapshot.proto",
        "snapshot_service.proto",
        "ui_controller_service.proto",
    ],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)
