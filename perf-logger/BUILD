load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.perf-logger",
    iml_files = ["perf-logger.iml"],
    tags = [
        "managed",
        "no_test_windows",  # b/77288863
    ],
    test_srcs = [
        "src/main/java",
        "src/test/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module, test]",
        "//tools/idea/platform/platform-api[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:gson[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
