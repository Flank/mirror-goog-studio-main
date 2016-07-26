# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_test(
  name = "perflib_tests",
  srcs = glob([
    ]),
  resources = [
      "//tools/base/perflib:perflib_tests.res",
    ],
  resource_strip_prefix = "tools/base/perflib/perflib_tests.resources",
  runtime_deps = [
      ":perflib_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=perflib_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "perflib_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/perflib:perflib",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/base/ddmlib:ddmlib_testlib",
          "//tools/base/common:common_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/trove4j",
      "//tools/base/bazel:util",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "perflib",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/perflib/perflib.resources",
  resources = [
      "//tools/base/perflib:perflib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/trove4j",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "perflib.res",
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
      "src/main/java": "perflib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "perflib_tests.res",
  srcs = glob(["src/test/resources/**"]),
  mappings = {"src/test/resources": "perflib_tests.resources"},
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
