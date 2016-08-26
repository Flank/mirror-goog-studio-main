# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.binary-resources",
    srcs = ["binary-resources/java"],
    test_srcs = ["binary-resources/javatests"],
    test_resources = ["binary-resources/testResources"],
    deps = [
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
