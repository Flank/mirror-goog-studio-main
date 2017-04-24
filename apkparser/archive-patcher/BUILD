load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.archive-patcher",
    srcs = [
        "applier/src/main/java",
        "explainer/src/main/java",
        "generator/src/main/java",
        "shared/src/main/java",
    ],
    tags = ["managed"],
    test_resources = [
        "applier/src/test/resources",
        "generator/src/test/resources",
    ],
    test_srcs = [
        "applier/src/test/java",
        "explainer/src/test/java",
        "generator/src/test/java",
        "shared/src/test/java",
        "sharedtest/src/main/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
