load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.binary-resources",
    srcs = ["binary-resources/src/main/java"],
    iml_files = ["binary-resources/android.sdktools.binary-resources.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["binary-resources/src/test/resources"],
    test_srcs = ["binary-resources/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

coverage_java_library(
    name = "tools.binary-resources",
    srcs = glob(["binary-resources/src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["binary-resources/src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["binary-resources/src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.binary-resources",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
