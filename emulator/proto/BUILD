load("//tools/base/bazel:proto.bzl", "maven_proto_library", "android_java_proto_library", "java_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    coordinates = "com.android.tools.emulator:proto:" + BASE_VERSION,
    description = "A protobuf messages to control Android Emulator",
    grpc_support = True,
    java_deps = [
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
    ],
    pom_name = "Emulator protos for Android Tools",
    resource_strip_prefix = "tools/base/emulator/proto",
    resources = glob(["*.proto"]),
    visibility = ["//visibility:public"],
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
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    visibility = ["//visibility:public"],
)
