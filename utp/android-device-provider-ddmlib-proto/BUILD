load("//tools/base/bazel:proto.bzl", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-device-provider-ddmlib-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-device-provider-ddmlib-proto:" + BASE_VERSION,
    protoc_grpc_version = "1.21.1",
    resource_strip_prefix = "tools/base/utp/android-device-provider-ddmlib-proto",
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
