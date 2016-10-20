load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.binary-resources",
    srcs = ["binary-resources/java"],
    tags = ["managed"],
    test_resources = ["binary-resources/testResources"],
    test_srcs = ["binary-resources/javatests"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
