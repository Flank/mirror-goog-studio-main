load("//tools/base/bazel:proto.bzl", "maven_proto_library")
load("//tools/base/bazel:maven.bzl", "import_maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

import_maven_library("android-test-plugin-host-coverage-proto", "android-test-plugin-host-coverage-proto_bzl")

maven_proto_library(
    name = "android-test-plugin-host-coverage-proto_bzl",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-coverage-proto:" + BASE_VERSION,
    protoc_grpc_version = "1.21.1",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-coverage-proto",
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
