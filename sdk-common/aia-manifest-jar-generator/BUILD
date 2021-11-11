load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "tools.proto",
    srcs = glob(["src/**/*.proto"]),
    resource_strip_prefix = "tools/base/sdk-common/aia-manifest-jar-generator/src",
    resources = glob(["src/**/*.proto"]),
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "proto",
    srcs = glob(["src/**/*.proto"]),
    visibility = ["//visibility:public"],
)
