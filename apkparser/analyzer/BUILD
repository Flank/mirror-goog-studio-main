load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.analyzer",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
    ],
)
