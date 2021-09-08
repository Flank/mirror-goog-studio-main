load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

coverage_java_library(
    name = "interpreter_common",
    srcs = glob([
        "src/main/java/com/android/tools/deploy/interpreter/**/*.java",
    ]),
    deps = [
        ":asm4",
        "//tools/base/annotations",
    ],
)

# Generates asm4.jar. Must be run manually and output copied to lib/asm4.jar.
# $ tools/base/bazel/bazel build tools/base/deploy/agent/runtime:jarjar_asm4
# $ cp bazel-bin/tools/base/deploy/agent/runtime/jarjar_asm4.jar tools/base/deploy/agent/runtime/lib/deploy_asm4.jar
java_jarjar(
    name = "jarjar_asm4",
    srcs = [
        "lib/asm-all-9.0.jar",
    ],
    rules = "jarjar_asm4_rules.txt",
    visibility = ["//visibility:private"],
)

java_import(
    name = "asm4",
    jars = [
        "lib/deploy_asm4.jar",
    ],
)

java_import(
    name = "kotlin-stdlib",
    jars = [
        "lib/kotlin-stdlib.jar",
    ],
)

kotlin_library(
    name = "eval4j",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    visibility = ["//visibility:private"],
    deps = [
        ":asm4",
        ":interpreter_common",
    ],
)

coverage_java_library(
    name = "runtime_java",
    srcs = [
        "//tools/base/deploy/sites:Sites.java",
    ] + glob([
        "src/main/java/com/android/tools/deploy/instrument/**/*.java",
        "src/main/java/com/android/tools/deploy/liveedit/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",

        # For prototyping LE, we will check in our own version of the
        # eval4j dependencies.
        ":asm4",
        ":eval4j",
        ":interpreter_common",
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
        ":eval4j",
        ":kotlin-stdlib",
        ":runtime_java",
        ":runtime_kotlin_test",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)

dex_library(
    name = "runtime",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [
        ":runtime_java",
        ":eval4j",
        ":interpreter_common",
        ":asm4",
        # ":kotlin-stdlib",
    ],
    visibility = ["//visibility:public"],
)
