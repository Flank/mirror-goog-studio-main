# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.layoutlib-api",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools:idea.annotations[module]",
    ],
    exports = [
        "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//tools/base/annotations:studio.android-annotations",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
