load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.perf-logger",
    iml_files = ["perf-logger.iml"],
    test_srcs = [
        "src/main/java",
        "src/test/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:gson[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
    ],
)
