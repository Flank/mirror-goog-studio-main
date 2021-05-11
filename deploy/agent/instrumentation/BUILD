load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")

coverage_java_library(
    name = "instrumentation_java",
    srcs = [
        "//tools/base/deploy/sites:Sites.java",
    ] + glob([
        "src/main/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

coverage_java_test(
    name = "instrumentation_java_test",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = [
        "-Dtest.suite.jar=instrumentation_java_test.jar",
        "-Ddashboards.enabled=true",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":instrumentation_java",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

dex_library(
    name = "instrumentation",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [":instrumentation_java"],
    visibility = ["//visibility:public"],
)
