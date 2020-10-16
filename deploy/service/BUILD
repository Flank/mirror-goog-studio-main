load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

android_java_proto_library(
    name = "deploy-service-proto",
    srcs = glob(["proto/**/*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "deploy.service",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    main_class = "com.android.deploy.service.DeployServiceMain",
    visibility = ["//visibility:public"],
    deps = [
        ":deploy-service-proto",
        "//tools/base/annotations",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/deployer:tools.deployer",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
    ],
)
