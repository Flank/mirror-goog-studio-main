package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "java_proto_library", "cc_grpc_proto_library")

java_proto_library(
    name = "profiler_java_proto",
    srcs = glob(["*.proto"]),
    grpc_support = 1,
)

cc_grpc_proto_library(
    name = "profiler_cc_proto",
    srcs = glob(["*.proto"]),
    grpc_support = 1,
    tags = ["no_windows"],
)
