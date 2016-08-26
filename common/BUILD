# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.common",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
    exports = [
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/base/annotations:studio.android-annotations",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.common",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    deps = [
        ":tools.common",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
)
