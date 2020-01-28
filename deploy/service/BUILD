load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

android_java_proto_library(
    name = "deploy-service-proto",
    srcs = glob(["proto/**/*.proto"]),
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.0.3",
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deploy.service",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.deploy.service.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-proto",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/deploy/service:deploy-service-proto",
    ],
)
