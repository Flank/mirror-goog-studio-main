# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "assetstudio_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/asset-studio/assetstudio_testlib.resources",
  resources = [
      "//tools/base/asset-studio:assetstudio_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/asset-studio:assetstudio",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/sdk-common:sdk-common_testlib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  exports = [
      "//tools/base/asset-studio:assetstudio",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "assetstudio_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":assetstudio_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=assetstudio_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "assetstudio",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/asset-studio/assetstudio.resources",
  resources = [
      "//tools/base/asset-studio:assetstudio.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/common:common",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/sdk-common:sdk-common",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  exports = [
      "//tools/base/common:common",
      "//tools/base/layoutlib-api:layoutlib-api",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "assetstudio.res",
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
      "src/main/java": "assetstudio.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "assetstudio_testlib.res",
  srcs = glob([
      "src/test/java/**/*",
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
      "src/test/java": "assetstudio_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
