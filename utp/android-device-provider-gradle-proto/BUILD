load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-device-provider-gradle-proto",
    srcs = glob(["*.proto"]),
    coordinates = "com.android.tools.utp:android-device-provider-gradle-proto:" + BASE_VERSION,
    description = "A protobuf messages used in a UTP plugin to provide a Gradle managed virtual Android devices",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto/",
    resources = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "libstudio.android-device-provider-gradle-proto",
    srcs = glob(["*.proto"]),
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto",
    visibility = ["//visibility:public"],
)
