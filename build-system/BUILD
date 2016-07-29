# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "builder-test-api.res",
  srcs = glob([
      "builder-test-api/src/main/java/**/*",
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
      "builder-test-api/src/main/java": "builder-test-api.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "builder-test-api_testlib.res",
  srcs = glob([
      "builder-test-api/src/test/java/**/*",
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
      "builder-test-api/src/test/java": "builder-test-api_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "manifest-merger_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":manifest-merger_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=manifest-merger_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "manifest-merger",
  srcs = glob([
      "manifest-merger/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/build-system/manifest-merger.resources",
  resources = [
      "//tools/base/build-system:manifest-merger.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/sdklib:sdklib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/sdk-common:sdk-common",
    ],
  exports = [
      "//tools/base/sdklib:sdklib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "manifest-merger_testlib",
  srcs = glob([
      "manifest-merger/src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/build-system/manifest-merger_testlib.resources",
  resources = [
      "//tools/base/build-system:manifest-merger_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/build-system:manifest-merger",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/sdk-common:sdk-common_testlib",
    ],
  exports = [
      "//tools/base/build-system:manifest-merger",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "builder-test-api_testlib",
  srcs = glob([
      "builder-test-api/src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/build-system/builder-test-api_testlib.resources",
  resources = [
      "//tools/base/build-system:builder-test-api_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/build-system:builder-test-api",
      "//tools/base/ddmlib:ddmlib",
      "//tools/base/ddmlib:ddmlib_testlib",
      "//tools/idea:lib/guava-18.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  exports = [
      "//tools/base/build-system:builder-test-api",
      "//tools/base/ddmlib:ddmlib",
      "//tools/base/ddmlib:ddmlib_testlib",
      "//tools/idea:lib/guava-18.0",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "manifest-merger.res",
  srcs = glob([
      "manifest-merger/src/main/java/**/*",
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
      "manifest-merger/src/main/java": "manifest-merger.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "builder-test-api",
  srcs = glob([
      "builder-test-api/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/build-system/builder-test-api.resources",
  resources = [
      "//tools/base/build-system:builder-test-api.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ddmlib:ddmlib",
      "//tools/idea:lib/guava-18.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  exports = [
      "//tools/base/ddmlib:ddmlib",
      "//tools/idea:lib/guava-18.0",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "builder-test-api_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":builder-test-api_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=builder-test-api_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "manifest-merger_testlib.res",
  srcs = glob([
      "manifest-merger/src/test/java/**/*",
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
      "manifest-merger/src/test/java": "manifest-merger_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
