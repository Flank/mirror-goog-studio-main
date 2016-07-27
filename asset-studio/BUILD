# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

TESTING_SRCS = [
  "src/test/java/com/android/assetstudiolib/BitmapGeneratorTest.java",
  "src/test/java/com/android/assetstudiolib/GeneratorTest.java",
]

java_library(
  name = "assetstudio_testing",
  srcs = TESTING_SRCS,
  deps = [
    ":assetstudio",
    "//tools/idea:lib/junit-4.12",
  ],
)

java_library(
  name = "assetstudio_testlib",
  srcs = glob(
      include = ["src/test/java/**/*.java"],
      exclude = TESTING_SRCS,
  ),
  deps = [
      ":assetstudio_testing",
      "@local_jdk//:langtools-neverlink",
      "//tools/base/asset-studio:assetstudio",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//tools/base/layoutlib-api:layoutlib-api",
              "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
              "//tools/idea:lib/hamcrest-core-1.3",
              "//tools/idea:lib/junit-4.12",
      "//tools/base/common:common_testlib",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/sdk-common:sdk-common",
          "//tools/base/sdklib:sdklib",
              "//tools/base/device_validator:dvlib",
              "//tools/idea:lib/httpcore-4.4.1",
              "//tools/idea:lib/httpmime-4.4.1",
              "//tools/idea:lib/httpclient-4.4.1",
              "//tools/idea:lib/fluent-hc-4.4.1",
              "//tools/adt/idea:android/lib/commons-compress-1.8.1",
              "//tools/idea:lib/gson-2.5",
              "//tools/base/repository:repository",
          "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
      "//tools/base/sdk-common:sdk-common_testlib",
          "//tools/base/sdklib:sdklib_testlib",
              "//tools/base/device_validator:dvlib_testlib",
              "//tools/base/repository:repository_testlib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
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
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=assetstudio_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
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
          "//tools/idea:lib/guava-18.0",
          "//tools/base/annotations:android-annotations",
      "//tools/base/layoutlib-api:layoutlib-api",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
          "//tools/idea:lib/hamcrest-core-1.3",
          "//tools/idea:lib/junit-4.12",
      "//tools/base/sdk-common:sdk-common",
          "//tools/base/sdklib:sdklib",
              "//tools/base/device_validator:dvlib",
              "//tools/idea:lib/httpcore-4.4.1",
              "//tools/idea:lib/httpmime-4.4.1",
              "//tools/idea:lib/httpclient-4.4.1",
              "//tools/idea:lib/fluent-hc-4.4.1",
              "//tools/adt/idea:android/lib/commons-compress-1.8.1",
              "//tools/idea:lib/gson-2.5",
              "//tools/base/repository:repository",
          "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "assetstudio.res",
  srcs = glob([
      "src/main/java/**/*",
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
      "src/main/java": "assetstudio.resources",
      "src/test/java": "assetstudio.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
