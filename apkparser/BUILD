# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "binary-resources",
  srcs = glob([
      "binary-resources/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/apkparser/binary-resources.resources",
  resources = [
      "//tools/base/apkparser:binary-resources.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/jsr305",
      "//tools/idea:lib/guava-18.0",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "binary-resources.res",
  srcs = glob([
      "binary-resources/java/**/*",
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
      "binary-resources/java": "binary-resources.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

java_library(
  name = "binary-resources_testlib",
  srcs = glob([
      "binary-resources/javatests/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/apkparser:binary-resources",
      "//tools/idea:lib/jsr305",
      "//tools/idea:lib/guava-18.0",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)
