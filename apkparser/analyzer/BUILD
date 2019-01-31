load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.analyzer",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.analyzer.iml"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/idea/.idea/libraries:baksmali",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)

java_library(
    name = "tools.analyzer",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.smali_baksmali",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.analyzer",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)
