load("//tools/base/bazel:proto.bzl", "java_proto_library", "maven_proto_library")
load("//tools/base/bazel:maven.bzl", "import_maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

import_maven_library("android-test-plugin-result-listener-gradle-proto", "android-test-plugin-result-listener-gradle-proto_bzl")

maven_proto_library(
    name = "android-test-plugin-result-listener-gradle-proto_bzl",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-result-listener-gradle-proto:" + BASE_VERSION,
    grpc_support = 1,
    java_exports = [
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
    ],
    protoc_grpc_version = "1.21.1",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-result-listener-gradle-proto",
    visibility = ["//visibility:public"],
)

# Android Studio only needs protobuf message without gRPC services.
java_proto_library(
    name = "studio.android-test-plugin-result-listener-gradle-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    proto_java_runtime_library = ["@maven//:com.google.protobuf.protobuf-java"],
    protoc_version = "3.0.0",  # Should match the version used in Studio.
    visibility = ["//visibility:public"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob(["src/main/proto/*.proto"]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
