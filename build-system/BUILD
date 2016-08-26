# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.builder-test-api",
    srcs = ["builder-test-api/src/main/java"],
    test_srcs = ["builder-test-api/src/test/java"],
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
    exports = [
        "//tools/base/ddmlib:studio.ddmlib",
        "//tools/idea/.idea/libraries:guava-tools",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "studio.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    test_srcs = ["manifest-merger/src/test/java"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = ["//tools/base/sdklib:studio.sdklib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
