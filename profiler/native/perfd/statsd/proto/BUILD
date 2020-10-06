package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "cc_grpc_proto_library")

cc_grpc_proto_library(
    name = "statsd_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = 0,
    include_prefix = "proto",
    tags = ["no_windows"],
)
