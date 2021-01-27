load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "network_inspector_java_proto",
    srcs = ["resources/proto/network-inspector.proto"],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
    visibility = ["//visibility:public"],
)

kotlin_library(
    name = "network-inspector-sources_undexed",
    srcs = glob(["src/**/*.kt"]),
    # do not sort: fake_android must come before latest_jar in the classpath to override small pieces of it
    deps = [
        ":network_inspector_java_proto",
        "//tools/base/network-inspector/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
        "//tools/base/bazel:studio-proto",
    ],
)

dex_library(
    name = "network-inspector-sources_dexed",
    dexer = "D8",
    flags = [
        "--min-api 26",  # Network inspector is only supported on O+ devices.
    ],
    jars = [
        ":network_inspector_java_proto",
        ":network-inspector-sources_undexed",
        "//tools/base/bazel:studio-proto",
    ],
)

java_library(
    name = "network-inspector-resources",
    resource_strip_prefix = "tools/base/network-inspector/resources",
    resources = glob(["resources/META-INF/**"]),
)

merge_jars(
    name = "network-inspector",
    out = "network-inspector.jar",
    jars = [
        ":network-inspector-sources_dexed",
        ":network-inspector-resources",
    ],
)
