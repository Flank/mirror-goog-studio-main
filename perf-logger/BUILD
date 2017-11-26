load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "perf-logger",
    srcs = ["src/main/java"],
    iml_files = ["perf-logger.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/testutils:tools.testutils[module]",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

java_test(
    name = "perf-logger_tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=perf-logger_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:truth",
    ],
)
