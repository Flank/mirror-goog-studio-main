load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-test-plugin-host-device-info-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-device-info-proto:" + BASE_VERSION,
    description = "A protobuf message used in a UTP plugin to export additional Android device information",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-device-info-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "libstudio.android-test-plugin-host-device-info-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)
