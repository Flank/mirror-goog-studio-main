# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.sdklib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/base/device_validator:studio.dvlib[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = [
        "//tools/base/common:studio.common",
        "//tools/base/device_validator:studio.dvlib",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.repository",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
