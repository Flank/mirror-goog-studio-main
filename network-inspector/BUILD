load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")

kotlin_library(
    name = "network-inspector-sources_undexed",
    srcs = glob(["src/**/*.kt"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
    ],
)

dex_library(
    name = "network-inspector-sources_dexed",
    dexer = "D8",
    jars = [":network-inspector-sources_undexed"],
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
