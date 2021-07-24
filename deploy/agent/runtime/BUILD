load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

java_import(
    name = "jetbrains_asm4",
    jars = [
        "lib/asm-all-9.0.jar",
    ],
)

kotlin_library(
    name = "eval4j",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    visibility = ["//visibility:private"],
    deps = [
        ":jetbrains_asm4",
    ],
)

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
        ":eval4j",
    ],
)

kotlin_library(
    name = "runtime_kotlin_test",
    srcs = glob([
        "src/test/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
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
        "lib/kotlin-stdlib.jar",
        ":eval4j",
        ":runtime_java",
        ":runtime_kotlin_test",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

dex_library(
    name = "runtime",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [
        ":runtime_java",
        ":eval4j",
        "lib/asm-all-9.0.jar",
        "lib/kotlin-stdlib.jar",
    ],
    visibility = ["//visibility:public"],
)
