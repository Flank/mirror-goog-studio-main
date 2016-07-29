# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "dvlib_testlib",
  srcs = glob([
      "dvlib/src/test/java/**/*.java",
      "dvlib/src/test/resources/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/device_validator/dvlib_testlib.resources",
  resources = [
      "//tools/base/device_validator:dvlib_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/device_validator:dvlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
    ],
  exports = [
      "//tools/base/device_validator:dvlib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "dvlib_testlib.res",
  srcs = glob([
      "dvlib/src/test/java/**/*",
      "dvlib/src/test/resources/**/*",
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
      "dvlib/src/test/java": "dvlib_testlib.resources",
      "dvlib/src/test/resources": "dvlib_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "dvlib.res",
  srcs = glob([
      "dvlib/src/main/java/**/*",
      "dvlib/src/main/resources/**/*",
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
      "dvlib/src/main/java": "dvlib.resources",
      "dvlib/src/main/resources": "dvlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "dvlib",
  srcs = glob([
      "dvlib/src/main/java/**/*.java",
      "dvlib/src/main/resources/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/device_validator/dvlib.resources",
  resources = [
      "//tools/base/device_validator:dvlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/common:common",
    ],
  exports = [
      "//tools/base/common:common",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "dvlib_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":dvlib_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=dvlib_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
