# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "sdklib_testlib.res",
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
      "src/test/java": "sdklib_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "sdklib",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/sdklib/sdklib.resources",
  resources = [
      "//tools/base/sdklib:sdklib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/common:common",
      "//tools/base/device_validator:dvlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/idea:lib/httpcore-4.4.1",
      "//tools/idea:lib/httpmime-4.4.1",
      "//tools/idea:lib/httpclient-4.4.1",
      "//tools/idea:lib/fluent-hc-4.4.1",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/repository:repository",
    ],
  exports = [
      "//tools/base/common:common",
      "//tools/base/device_validator:dvlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/idea:lib/httpcore-4.4.1",
      "//tools/idea:lib/httpmime-4.4.1",
      "//tools/idea:lib/httpclient-4.4.1",
      "//tools/idea:lib/fluent-hc-4.4.1",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/repository:repository",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "sdklib_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/sdklib/sdklib_testlib.resources",
  resources = [
      "//tools/base/sdklib:sdklib_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/sdklib:sdklib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/device_validator:dvlib",
      "//tools/base/device_validator:dvlib_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/idea:lib/httpcore-4.4.1",
      "//tools/idea:lib/httpmime-4.4.1",
      "//tools/idea:lib/httpclient-4.4.1",
      "//tools/idea:lib/fluent-hc-4.4.1",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/repository:repository",
      "//tools/base/repository:repository_testlib",
    ],
  exports = [
      "//tools/base/sdklib:sdklib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/device_validator:dvlib",
      "//tools/base/device_validator:dvlib_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/idea:lib/httpcore-4.4.1",
      "//tools/idea:lib/httpmime-4.4.1",
      "//tools/idea:lib/httpclient-4.4.1",
      "//tools/idea:lib/fluent-hc-4.4.1",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/repository:repository",
      "//tools/base/repository:repository_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "sdklib_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":sdklib_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=sdklib_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "sdklib.res",
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
      "src/main/java": "sdklib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
