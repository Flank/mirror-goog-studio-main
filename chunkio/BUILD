# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "chunkio",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/chunkio/chunkio.resources",
  resources = [
      "//tools/base/chunkio:chunkio.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "chunkio.res",
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
      "src/main/java": "chunkio.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
