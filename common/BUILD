# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "common_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/common:common",
          "//tools/idea:lib/guava-18.0",
          "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "common",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/common/common.resources",
  resources = [
      "//tools/base/common:common.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/guava-18.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/annotations:android-annotations",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "common.res",
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
      "src/main/java": "common.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "common_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":common_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=common_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
