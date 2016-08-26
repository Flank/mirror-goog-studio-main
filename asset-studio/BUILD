# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.assetstudio",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
    exports = [
        "//tools/base/common:studio.common",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
