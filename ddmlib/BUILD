# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.ddmlib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:easymock-tools[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = [
        "//tools/base/common:studio.common",
        "//tools/idea/.idea/libraries:kxml2",
    ],
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
