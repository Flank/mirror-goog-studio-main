# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "ninepatch",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/ninepatch/ninepatch.resources",
  resources = [
      "//tools/base/ninepatch:ninepatch.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "ninepatch.res",
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
      "src/main/java": "ninepatch.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

java_library(
  name = "ninepatch_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
      "src/test/resources/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ninepatch:ninepatch",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)
