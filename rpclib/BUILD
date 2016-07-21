# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "rpclib.res",
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
      "src/main/java": "rpclib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "rpclib",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/rpclib/rpclib.resources",
  resources = [
      "//tools/base/rpclib:rpclib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:annotations",
          "//tools/idea:annotations-common",
      "//tools/idea:util",
          "//tools/idea:lib/trove4j",
          "//tools/idea:util-rt",
          "//tools/idea:lib/jsr166e",
          "//tools/idea:lib/snappy-in-java-0.3.1",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:platform-api",
          "//tools/idea:extensions",
              "//tools/idea:lib/xstream-1.4.8",
              "//tools/idea:lib/jdom",
          "//tools/idea:core-api",
              "//tools/idea:lib/picocontainer",
              "//tools/idea:platform-resources-en",
          "//tools/idea:lib/jgoodies-forms",
          "//tools/idea:forms_rt",
          "//tools/idea:projectModel-api",
              "//tools/idea:jps-model-api",
          "//tools/idea:analysis-api",
              "//tools/idea:editor-ui-api",
      "//tools/idea:lib/guava-18.0",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
