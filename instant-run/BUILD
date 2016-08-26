# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.instant-run-common",
    srcs = ["instant-run-common/src/main/java"],
    deps = ["//tools/base/annotations:studio.android-annotations[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "studio.instant-run-client",
    srcs = ["instant-run-client/src/main/java"],
    test_srcs = ["instant-run-client/src/test/java"],
    test_resources = ["instant-run-client/src/test/resources"],
    deps = [
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
