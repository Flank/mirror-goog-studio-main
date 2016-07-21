# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "layoutlib-api.res",
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
      "src/main/java": "layoutlib-api.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "layoutlib-api_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":layoutlib-api_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=layoutlib-api_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "layoutlib-api",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/layoutlib-api/layoutlib-api.resources",
  resources = [
      "//tools/base/layoutlib-api:layoutlib-api.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:annotations",
          "//tools/idea:annotations-common",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "layoutlib-api_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/layoutlib-api:layoutlib-api",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
          "//tools/idea:lib/hamcrest-core-1.3",
          "//tools/idea:lib/junit-4.12",
          "//tools/base/annotations:android-annotations",
      "//tools/idea:annotations",
          "//tools/idea:annotations-common",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
