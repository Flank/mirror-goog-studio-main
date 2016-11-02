load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.android-annotations",
    srcs = ["src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

maven_java_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    pom = ":pom",
)

maven_pom(
    name = "pom",
    group = "com.android.tools",
    artifact = "annotations",
    source = "//tools/buildSrc/base:base_version",
)