load("//tools/base/bazel:proto.bzl", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-test-plugin-host-additional-test-output-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-additional-test-output-proto:" + BASE_VERSION,
    description = "A protobuf messages used in a UTP plugin to export additional test outputs",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-additional-test-output-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)
