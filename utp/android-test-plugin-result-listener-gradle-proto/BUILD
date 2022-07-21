load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-test-plugin-result-listener-gradle-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-result-listener-gradle-proto:" + BASE_VERSION,
    description = "A protobuf messages used in a UTP plugin to listen test results",
    grpc_support = True,
    java_deps = [
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
    ],
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-result-listener-gradle-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)

# Android Studio only needs protobuf message without gRPC services.
android_java_proto_library(
    name = "libstudio.android-test-plugin-result-listener-gradle-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)
