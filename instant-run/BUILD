# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "instant-run-client.res",
  srcs = glob([
      "instant-run-client/src/main/java/**/*",
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
      "instant-run-client/src/main/java": "instant-run-client.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "instant-run-common",
  srcs = glob([
      "instant-run-common/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/instant-run/instant-run-common.resources",
  resources = [
      "//tools/base/instant-run:instant-run-common.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/annotations:android-annotations",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "instant-run-common.res",
  srcs = glob([
      "instant-run-common/src/main/java/**/*",
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
      "instant-run-common/src/main/java": "instant-run-common.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "instant-run-client_testlib",
  srcs = glob([
      "instant-run-client/src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/instant-run:instant-run-client",
      "//tools/base/instant-run:instant-run-common",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/base/ddmlib:ddmlib_testlib",
          "//tools/base/common:common_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "instant-run-client_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":instant-run-client_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=instant-run-client_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "instant-run-client",
  srcs = glob([
      "instant-run-client/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/instant-run/instant-run-client.resources",
  resources = [
      "//tools/base/instant-run:instant-run-client.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/instant-run:instant-run-common",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
