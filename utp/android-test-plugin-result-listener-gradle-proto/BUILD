load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_pom")

java_proto_library(
    name = "android-test-plugin-result-listener-gradle-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    grpc_support = 1,
    java_deps = [
        "//tools/base/third_party:io.grpc_grpc-core",
        "//tools/base/third_party:io.grpc_grpc-netty",
        "//tools/base/third_party:io.grpc_grpc-stub",
        "//tools/base/third_party:io.grpc_grpc-protobuf",
    ],
    pom = ":pom",
    protoc_grpc_version = "1.21.1",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-result-listener-gradle-proto",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "android-test-plugin-result-listener-gradle-proto",
    group = "com.android.tools.utp",
    source = "//tools/buildSrc/base:base_version",
)

# Android Studio only needs protobuf message without gRPC services.
java_proto_library(
    name = "studio.android-test-plugin-result-listener-gradle-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    proto_java_runtime_library = ["//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.5.1:jar"],
    protoc_version = "3.0.0",  # Should match the version used in Studio.
    visibility = ["//visibility:public"],
)
