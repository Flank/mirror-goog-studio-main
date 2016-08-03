# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "instant-run-common",
    srcs = ["instant-run-common/src/main/java"],
    deps = ["//tools/base/annotations:android-annotations[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "instant-run-client",
    srcs = ["instant-run-client/src/main/java"],
    test_srcs = ["instant-run-client/src/test/java"],
    test_resources = ["instant-run-client/src/test/resources"],
    deps = [
        "//tools/base/instant-run:instant-run-common[module]",
        "//tools/base/ddmlib:ddmlib[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
