# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "ddmlib",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/ddmlib/ddmlib.resources",
  resources = [
      "//tools/base/ddmlib:ddmlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/common:common",
          "//tools/idea:lib/guava-18.0",
          "//tools/base/annotations:android-annotations",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "ddmlib_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/base/common:common_testlib",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "ddmlib.res",
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
      "src/main/java": "ddmlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)
