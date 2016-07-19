# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "chunkio-processor",
  srcs = glob([
      "src/**/*.java",
    ]),
  resource_strip_prefix="tools/base/chunkio-processor/chunkio-processor.resources",
  resources = [
      "//tools/base/chunkio-processor:chunkio-processor.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/chunkio:chunkio",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "chunkio-processor.res",
  srcs = glob([
      "src/**/*",
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
      "src": "chunkio-processor.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)
