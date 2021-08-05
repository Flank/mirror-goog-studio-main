load("//tools/base/bazel:proto.bzl", "maven_proto_library", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "import_maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

import_maven_library("proto", "proto_bzl")

maven_proto_library(
    name = "proto_bzl",
    srcs = glob(["*.proto"]),
    coordinates = "com.android.tools.emulator:proto:" + BASE_VERSION,
    grpc_support = 1,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    protoc_grpc_version = "1.21.1",
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
    grpc_support = 1,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob(["*.proto"]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
