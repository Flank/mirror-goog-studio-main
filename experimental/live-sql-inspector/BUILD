load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "cc_grpc_proto_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "live_sql_viewer_java",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/experimental/live-sql-inspector/src/main/resources/",
    resources = glob(["src/main/resources/META-INF/**"]),
    deps = [
        ":live_sql_java_proto",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
        "//tools/base/bazel:studio-proto",
    ],
)

android_java_proto_library(
    name = "live_sql_java_proto",
    srcs = glob(["proto/*.proto"]),
    grpc_support = 1,
    protoc_grpc_version = "1.0.3",
)

dex_library(
    name = "live_sql_viewer_dex",
    jars = [
        ":live_sql_java_proto",
        ":live_sql_viewer_java",
        "//tools/base/bazel:studio-proto",
    ],
)
