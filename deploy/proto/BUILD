load("//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "android_java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//tools/base/deploy:__subpackages__"])

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = glob(["*.proto"]),
    include_prefix = "proto",
    tags = ["no_windows"],
)

android_java_proto_library(
    name = "deploy_java_proto",
    srcs = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "proto_tool",
    srcs = ["proto_tool.cc"],
    copts = ["-I$(GENDIR)/tools/base/deploy/proto"],
    tags = ["no_windows"],
    deps = ["//tools/base/deploy/proto:cc_proto"],
)

filegroup(
    name = "protobuffer_src",
    srcs = [
        "deploy.proto",
    ],
    visibility = ["//visibility:public"],
)
