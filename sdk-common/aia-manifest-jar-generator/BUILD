load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

java_proto_library(
    name = "tools.proto",
    srcs = glob(["src/**/*.proto"]),
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "proto",
    srcs = glob(["src/**/*.proto"]),
    visibility = ["//visibility:public"],
)
