# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "binary-resources",
    srcs = ["binary-resources/java"],
    test_srcs = ["binary-resources/javatests"],
    test_resources = ["binary-resources/testResources"],
    deps = [
        "//tools/idea:lib/jsr305",
        "//tools/idea:lib/guava-18.0",
        "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28[test]",
        "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources[test]",
        "//tools/idea:lib/hamcrest-core-1.3[test]",
        "//tools/idea:lib/junit-4.12[test]",
        "//tools/base/testutils:testutils[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
