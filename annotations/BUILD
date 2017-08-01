load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.android-annotations",
    srcs = ["src/main/java"],
    iml_files = ["android-annotations.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

maven_java_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "annotations",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)
