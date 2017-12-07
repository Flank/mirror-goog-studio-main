load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.perf-logger",
    srcs = ["src/main/java"],
    iml_files = ["perf-logger.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:gson"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/testutils:studio.testutils[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
