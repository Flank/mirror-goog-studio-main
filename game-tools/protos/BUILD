load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "game-tools-protos",
    srcs = glob(["*.proto"]),
    grpc_support = True,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.0.3",
    visibility = ["//visibility:public"],
)
