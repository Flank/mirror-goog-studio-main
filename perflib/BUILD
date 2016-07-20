# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "perflib_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/perflib:perflib",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/base/ddmlib:ddmlib_testlib",
          "//tools/base/common:common_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/trove4j",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "perflib",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/perflib/perflib.resources",
  resources = [
      "//tools/base/perflib:perflib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/ddmlib:ddmlib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/trove4j",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "perflib.res",
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
      "src/main/java": "perflib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

# java_test(
#     name = "perflib_tests",
#     runtime_deps = [
#         ":perflib_testlib",
#     ],
#     test_class = "com.android.tools.bazel.GenericTestSuite",
#     args = [ ":perflib.jar"]
#     deps = [
#       "//tools/base/bazel:test"
#     ]
# )