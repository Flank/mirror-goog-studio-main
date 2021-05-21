load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")

coverage_java_library(
    name = "runtime_java",
    srcs = [
        "//tools/base/deploy/sites:Sites.java",
    ] + glob([
        "src/main/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",

        # For prototyping LE, we will check in our own version of the
        # eval4j dependencies.
        "lib/asm-all-9.0.jar",
        "lib/kotlin-eval4j.jar",
    ],
)

coverage_java_test(
    name = "runtime_java_test",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = [
        "-Dtest.suite.jar=runtime_java_test.jar",
        "-Ddashboards.enabled=true",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "lib/asm-all-9.0.jar",
        "lib/kotlin-eval4j.jar",
        "lib/kotlin-stdlib.jar",
        ":runtime_java",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

dex_library(
    name = "runtime",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [":runtime_java"],
    visibility = ["//visibility:public"],
)
