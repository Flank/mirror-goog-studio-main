load("//tools/base/bazel:android.bzl", "dex_library")

java_library(
    name = "test-inspector_undexed",
    srcs = glob(["src/**/*.java"]),
    resource_strip_prefix = "tools/base/app-inspection/tests/test-inspector",
    resources = glob(["META-INF/**"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
    ],
)

dex_library(
    name = "test-inspector",

    # TODO: b/150637179 to move this to D8. That Java Service Provider oin META-INF
    #       is not found in the final jar for some reason why used with D8.
    dexer = "DX",
    jars = [
        ":test-inspector_undexed",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
    ],
    visibility = ["//visibility:public"],
)
