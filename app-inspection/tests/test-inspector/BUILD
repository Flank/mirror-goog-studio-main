load("//tools/base/bazel:android.bzl", "dex_library")

java_library(
    name = "test-inspector_undexed",
    srcs = glob(["**/*.java"]),
    resource_strip_prefix = "tools/base/app-inspection/tests/test-inspector",
    resources = glob(["META-INF/**"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
    ],
)

dex_library(
    name = "test-inspector",
    jars = [":test-inspector_undexed"],
    visibility = ["//visibility:public"],
)
