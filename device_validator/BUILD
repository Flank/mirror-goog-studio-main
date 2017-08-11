load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.dvlib",
    # do not sort: must match IML order
    srcs = ["dvlib/src/main/java"],
    iml_files = ["dvlib/dvlib.iml"],
    resources = ["dvlib/src/main/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.common"],
    deps = ["//tools/base/common:studio.common[module]"],
)

maven_java_library(
    name = "tools.dvlib",
    srcs = glob(["dvlib/src/main/java/**"]),
    pom = ":pom",
    resources = glob(["dvlib/src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

maven_pom(
    name = "pom",
    artifact = "dvlib",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)
