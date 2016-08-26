# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.sdk-common",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:builder-model",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/build-system:studio.builder-test-api[module]",
        "//tools/idea/.idea/libraries:easymock-tools[test]",
        "//tools/idea/.idea/libraries:bouncy-castle",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
    exports = [
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/base/common:studio.common",
        "//tools/base/sdklib:studio.sdklib",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:builder-model",
        "//tools/idea/.idea/libraries:gson",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
