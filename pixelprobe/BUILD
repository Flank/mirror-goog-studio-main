# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "pixelprobe.res",
  srcs = glob([
      "src/main/generated/**/*",
      "src/main/java/**/*",
      "src/test/tools/**/*",
      "src/main/resources/**/*",
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
      "src/main/resources": "pixelprobe.resources",
      "src/main/java": "pixelprobe.resources",
      "src/test/tools": "pixelprobe.resources",
      "src/main/generated": "pixelprobe.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "pixelprobe_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/pixelprobe:pixelprobe",
      "//tools/base/chunkio:chunkio",
      "//tools/base/chunkio-processor:chunkio-processor",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "pixelprobe_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":pixelprobe_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=pixelprobe_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "pixelprobe",
  srcs = glob([
      "src/main/generated/**/*.java",
      "src/main/java/**/*.java",
      "src/test/tools/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/pixelprobe/pixelprobe.resources",
  resources = [
      "//tools/base/pixelprobe:pixelprobe.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/chunkio:chunkio",
      "//tools/base/chunkio-processor:chunkio-processor",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
