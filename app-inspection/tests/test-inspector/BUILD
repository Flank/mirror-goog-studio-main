load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")

java_library(
    name = "test-inspector-sources_undexed",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
        "//tools/base/fakeandroid:android-mock",
    ],
)

# D8 doesn't not pull the resources into the output jar. We first build the dex code first,
# then we build the resources jar seperately and finally merge them back together.
# If more targets start needing to include resources in their dex_library targets,
# we should definitely move this as part of dex_library instead.
dex_library(
    name = "test-inspector-sources_dexed",
    dexer = "D8",
    jars = [
        ":test-inspector-sources_undexed",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
    ],
)

java_library(
    name = "test-inspector-resources",
    resource_strip_prefix = "tools/base/app-inspection/tests/test-inspector",
    resources = glob(["META-INF/**"]),
)

merge_jars(
    name = "test-inspector_merged",
    out = "test-inspector_merged.jar",
    jars = [
        ":test-inspector-sources_dexed",
        ":test-inspector-resources",
    ],
)

java_import(
    name = "test-inspector",
    jars = [":test-inspector_merged.jar"],
    visibility = ["//visibility:public"],
)
