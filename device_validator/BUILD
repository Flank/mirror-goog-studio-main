# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "dvlib_testlib",
  srcs = glob([
      "dvlib/src/test/java/**/*.java",
      "dvlib/src/test/resources/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/device_validator:dvlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/common:common_testlib",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "dvlib.res",
  srcs = glob([
      "dvlib/src/main/java/**/*",
      "dvlib/src/main/resources/**/*",
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
      "dvlib/src/main/java": "dvlib.resources",
      "dvlib/src/main/resources": "dvlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

java_library(
  name = "dvlib",
  srcs = glob([
      "dvlib/src/main/java/**/*.java",
      "dvlib/src/main/resources/**/*.java",
    ]),
  resource_strip_prefix="tools/base/device_validator/dvlib.resources",
  resources = [
      "//tools/base/device_validator:dvlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/common:common",
          "//tools/idea:lib/guava-18.0",
          "//tools/base/annotations:android-annotations",
    ],
  visibility = ["//visibility:public"],
)
