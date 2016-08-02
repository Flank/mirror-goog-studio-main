# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "perflib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/ddmlib:ddmlib[module]",
        "//tools/idea:lib/hamcrest-core-1.3[test]",
        "//tools/idea:lib/junit-4.12[test]",
        "//tools/idea:lib/trove4j",
        "//tools/base/testutils:testutils[module, test]",
        "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
