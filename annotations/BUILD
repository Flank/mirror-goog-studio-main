load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom", "VERSION")
load("//tools/base/bazel:kotlin.bzl", "maven_library")

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

maven_java_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/annotations",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "annotations",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)

maven_library(
    name = "annotations_bzl",
    srcs = glob(["src/main/java/**"]),
    coordinates = "com.android.tools:annotations:" + VERSION,
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/annotations",
    visibility = ["//visibility:public"],
)
