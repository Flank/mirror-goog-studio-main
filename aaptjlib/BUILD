# Bazel (https://bazel.io/) BUILD file for aaptjlib library.
load("//tools/base/bazel:bazel.bzl", "fileset")

exports_files(glob(["src/main/resources/**"]))

licenses(["notice"])  # Apache License 2.0

load("//tools/base/bazel:utils.bzl", "srcjar")
load("//tools/base/bazel:bazel.bzl", "fileset")

srcjar(
    name = "srcjar",
    java_library = ":aaptjlib",
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

java_library(
    name = "aaptjlib",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = [":resources"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
    ],
)

java_test(
    name = "aaptjlib_tests",
    size = "small",
    srcs = glob([
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":aaptjlib",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
