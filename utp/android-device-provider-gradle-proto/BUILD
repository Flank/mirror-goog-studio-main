load("//tools/base/bazel:proto.bzl", "java_proto_library", "maven_proto_library")
load("//tools/base/bazel:maven.bzl", "import_maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

import_maven_library("android-device-provider-gradle-proto", "android-device-provider-gradle-proto_bzl")

maven_proto_library(
    name = "android-device-provider-gradle-proto_bzl",
    srcs = glob(["*.proto"]),
    coordinates = "com.android.tools.utp:android-device-provider-gradle-proto:" + BASE_VERSION,
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto",
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "studio.android-device-provider-gradle-proto",
    srcs = glob(["*.proto"]),
    proto_java_runtime_library = ["@maven//:com.google.protobuf.protobuf-java"],
    protoc_version = "3.0.0",  # Should match the version used in Studio.
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto",
    visibility = ["//visibility:public"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob(["*.proto"]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
