load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "game-tools-protos",
    srcs = glob(["*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)
