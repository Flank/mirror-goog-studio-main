load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.android-annotations",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.android-annotations.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

maven_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    coordinates = "com.android.tools:annotations:" + BASE_VERSION,
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/annotations",
    visibility = ["//visibility:public"],
)
