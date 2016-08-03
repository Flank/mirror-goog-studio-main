# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dvlib",
    srcs = [
        "dvlib/src/main/java",
        "dvlib/src/main/resources",
    ],
    test_srcs = [
        "dvlib/src/test/java",
        "dvlib/src/test/resources",
    ],
    deps = [
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools/base/common:common[module]",
    ],
    exports = ["//tools/base/common:common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
