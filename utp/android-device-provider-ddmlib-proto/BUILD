load("//tools/base/bazel:proto.bzl", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-device-provider-ddmlib-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-device-provider-ddmlib-proto:" + BASE_VERSION,
    description = "A protobuf messages used in a UTP plugin to provide Android device using ddmlib",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/utp/android-device-provider-ddmlib-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)
