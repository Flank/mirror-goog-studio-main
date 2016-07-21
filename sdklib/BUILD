# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

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
          "//tools/idea:lib/guava-18.0",
          "//tools/base/annotations:android-annotations",
      "//tools/base/device_validator:dvlib",
      "//tools/base/layoutlib-api:layoutlib-api",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
          "//tools/idea:lib/hamcrest-core-1.3",
          "//tools/idea:lib/junit-4.12",
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
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/sdklib:sdklib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//tools/base/device_validator:dvlib",
          "//tools/base/layoutlib-api:layoutlib-api",
              "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
              "//tools/idea:lib/hamcrest-core-1.3",
              "//tools/idea:lib/junit-4.12",
          "//tools/idea:lib/httpcore-4.4.1",
          "//tools/idea:lib/httpmime-4.4.1",
          "//tools/idea:lib/httpclient-4.4.1",
          "//tools/idea:lib/fluent-hc-4.4.1",
          "//tools/adt/idea:android/lib/commons-compress-1.8.1",
          "//tools/idea:lib/gson-2.5",
          "//tools/base/repository:repository",
      "//tools/base/common:common_testlib",
      "//tools/base/device_validator:dvlib_testlib",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
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
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=sdklib_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
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
