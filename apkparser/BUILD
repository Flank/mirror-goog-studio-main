# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "binary-resources_testlib.res",
  srcs = glob([
      "binary-resources/javatests/**/*",
      "binary-resources/testResources/**/*",
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
      "binary-resources/testResources": "binary-resources_testlib.resources",
      "binary-resources/javatests": "binary-resources_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "binary-resources",
  srcs = glob([
      "binary-resources/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/apkparser/binary-resources.resources",
  resources = [
      "//tools/base/apkparser:binary-resources.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/jsr305",
      "//tools/idea:lib/guava-18.0",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/testutils:testutils",
          "//tools/base/common:common",
              "//tools/base/annotations:android-annotations",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "binary-resources_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":binary-resources_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=binary-resources_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "binary-resources.res",
  srcs = glob([
      "binary-resources/java/**/*",
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
      "binary-resources/java": "binary-resources.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "binary-resources_testlib",
  srcs = glob([
      "binary-resources/javatests/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/apkparser/binary-resources_testlib.resources",
  resources = [
      "//tools/base/apkparser:binary-resources_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/apkparser:binary-resources",
      "//tools/idea:lib/jsr305",
      "//tools/idea:lib/guava-18.0",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/testutils:testutils",
          "//tools/base/common:common",
              "//tools/base/annotations:android-annotations",
      "//tools/base/testutils:testutils_testlib",
          "//tools/base/common:common_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
