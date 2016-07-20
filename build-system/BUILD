# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

fileset(
  name = "builder-test-api.res",
  srcs = glob([
      "builder-test-api/src/main/java/**/*",
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
      "builder-test-api/src/main/java": "builder-test-api.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

java_library(
  name = "manifest-merger",
  srcs = glob([
      "manifest-merger/src/main/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/build-system/manifest-merger.resources",
  resources = [
      "//tools/base/build-system:manifest-merger.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/sdklib:sdklib",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
              "//tools/base/annotations:android-annotations",
          "//tools/base/device_validator:dvlib",
          "//tools/base/layoutlib-api:layoutlib-api",
              "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
          "//tools/idea:lib/httpcore-4.4.1",
          "//tools/idea:lib/httpmime-4.4.1",
          "//tools/idea:lib/httpclient-4.4.1",
          "//tools/idea:lib/fluent-hc-4.4.1",
          "//tools/adt/idea:android/lib/commons-compress-1.8.1",
          "//tools/base/repository:repository",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/sdk-common:sdk-common",
          "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "manifest-merger_testlib",
  srcs = glob([
      "manifest-merger/src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/build-system:manifest-merger",
          "//tools/base/sdklib:sdklib",
              "//tools/base/common:common",
                  "//tools/idea:lib/guava-18.0",
                  "//tools/base/annotations:android-annotations",
              "//tools/base/device_validator:dvlib",
              "//tools/base/layoutlib-api:layoutlib-api",
                  "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
                  "//tools/idea:lib/hamcrest-core-1.3",
                  "//tools/idea:lib/junit-4.12",
              "//tools/idea:lib/httpcore-4.4.1",
              "//tools/idea:lib/httpmime-4.4.1",
              "//tools/idea:lib/httpclient-4.4.1",
              "//tools/idea:lib/fluent-hc-4.4.1",
              "//tools/adt/idea:android/lib/commons-compress-1.8.1",
              "//tools/idea:lib/gson-2.5",
              "//tools/base/repository:repository",
      "//tools/base/sdklib:sdklib_testlib",
          "//tools/base/common:common_testlib",
          "//tools/base/device_validator:dvlib_testlib",
          "//tools/base/layoutlib-api:layoutlib-api_testlib",
          "//tools/base/repository:repository_testlib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/sdk-common:sdk-common",
          "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
      "//tools/base/sdk-common:sdk-common_testlib",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "builder-test-api_testlib",
  srcs = glob([
      "builder-test-api/src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/build-system:builder-test-api",
          "//tools/base/ddmlib:ddmlib",
              "//tools/base/common:common",
                  "//tools/idea:lib/guava-18.0",
                  "//tools/base/annotations:android-annotations",
              "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/base/ddmlib:ddmlib_testlib",
          "//tools/base/common:common_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "manifest-merger.res",
  srcs = glob([
      "manifest-merger/src/main/java/**/*",
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
      "manifest-merger/src/main/java": "manifest-merger.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
   visibility = ["//visibility:public"],
)

java_library(
  name = "builder-test-api",
  srcs = glob([
      "builder-test-api/src/main/java/**/*.java",
    ]),
  resource_strip_prefix="tools/base/build-system/builder-test-api.resources",
  resources = [
      "//tools/base/build-system:builder-test-api.res",
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
    ],
  visibility = ["//visibility:public"],
)
