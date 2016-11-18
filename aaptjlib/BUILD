# Bazel (https://bazel.io/) BUILD file for aaptjlib library.
load("//tools/base/bazel:bazel.bzl", "fileset")

exports_files(glob(["src/main/resources/**"]))

licenses(["notice"])  # Apache License 2.0

filegroup(
    name = "sources",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

java_library(
    name = "aaptjlib",
    srcs = [":sources"],
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
    ],
)

java_test(
    name = "aaptj_tests",
    srcs = glob([
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = ["manual"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":apkzlib",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-all",
    ],
)
