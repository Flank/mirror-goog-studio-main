load("//tools/base/bazel:proto.bzl", "java_proto_library", "maven_proto_library")
load("//tools/base/bazel:maven.bzl", "import_maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

import_maven_library("android-test-plugin-host-retention-proto", "android-test-plugin-host-retention-proto_bzl")

maven_proto_library(
    name = "android-test-plugin-host-retention-proto_bzl",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-retention-proto:" + BASE_VERSION,
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-retention-proto",
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "studio.android-test-plugin-host-retention-proto",
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
