# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "common",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools:idea/lib/guava-18.0",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools/base/annotations:android-annotations[module]",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.28:jar[test]",
    ],
    exports = [
        "//tools:idea/lib/guava-18.0",
        "//tools/base/annotations:android-annotations",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
