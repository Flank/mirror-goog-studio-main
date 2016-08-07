# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ddmlib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:common[module]",
        "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
        "//prebuilts/tools/common/easymock-tools:easymock-3.1[test]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools/base/testutils:testutils[test]",
    ],
    exports = [
        "//tools/base/common:common",
        "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
    ],
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
