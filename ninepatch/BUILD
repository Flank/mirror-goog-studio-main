# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "ninepatch",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/ninepatch/ninepatch.resources",
  resources = [
      "//tools/base/ninepatch:ninepatch.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "ninepatch.res",
  srcs = glob([
      "src/main/java/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "src/main/java": "ninepatch.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "ninepatch_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":ninepatch_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=ninepatch_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "ninepatch_testlib.res",
  srcs = glob([
      "src/test/java/**/*",
      "src/test/resources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "src/test/java": "ninepatch_testlib.resources",
      "src/test/resources": "ninepatch_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "ninepatch_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
      "src/test/resources/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/ninepatch/ninepatch_testlib.resources",
  resources = [
      "//tools/base/ninepatch:ninepatch_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ninepatch:ninepatch",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
