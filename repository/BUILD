# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "repository_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/repository/repository_testlib.resources",
  resources = [
      "//tools/base/repository:repository_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/repository:repository",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/guava-18.0",
      "//tools/idea:lib/junit",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//prebuilts/tools/common/m2:repository/com/google/jimfs/jimfs/1.1/jimfs-1.1",
      "//prebuilts/tools/common/m2:repository/com/google/jimfs/jimfs/1.1/jimfs-1.1-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "repository.res",
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
      "src/main/java": "repository.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "repository",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/repository/repository.resources",
  resources = [
      "//tools/base/repository:repository.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/guava-18.0",
      "//tools/idea:lib/junit",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/adt/idea:android/lib/commons-compress-1.8.1",
      "//prebuilts/tools/common/m2:repository/com/google/jimfs/jimfs/1.1/jimfs-1.1",
      "//prebuilts/tools/common/m2:repository/com/google/jimfs/jimfs/1.1/jimfs-1.1-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "repository_testlib.res",
  srcs = glob([
      "src/test/java/**/*",
      "src/test/resources/**/*",
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
      "src/test/java": "repository_testlib.resources",
      "src/test/resources": "repository_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "repository_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":repository_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=repository_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
