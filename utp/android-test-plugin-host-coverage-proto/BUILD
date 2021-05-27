load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_pom")

java_proto_library(
    name = "android-test-plugin-host-coverage-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    pom = ":pom",
    protoc_grpc_version = "1.21.1",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-coverage-proto",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "android-test-plugin-host-coverage-proto",
    group = "com.android.tools.utp",
    source = "//tools/buildSrc/base:base_version",
)
