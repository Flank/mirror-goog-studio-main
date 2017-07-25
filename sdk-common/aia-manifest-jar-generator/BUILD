load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

java_proto_library(
    name = "proto",
    srcs = glob(["src/**/*.proto"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "protos",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)
