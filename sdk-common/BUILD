# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "sdk-common",
  srcs = glob([
      "src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/sdk-common/sdk-common.resources",
  resources = [
      "//tools/base/sdk-common:sdk-common.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/layoutlib-api:layoutlib-api",
          "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
          "//tools/idea:lib/hamcrest-core-1.3",
          "//tools/idea:lib/junit-4.12",
          "//tools/base/annotations:android-annotations",
      "//tools/base/common:common",
          "//tools/idea:lib/guava-18.0",
      "//tools/base/sdklib:sdklib",
          "//tools/base/device_validator:dvlib",
          "//tools/idea:lib/httpcore-4.4.1",
          "//tools/idea:lib/httpmime-4.4.1",
          "//tools/idea:lib/httpclient-4.4.1",
          "//tools/idea:lib/fluent-hc-4.4.1",
          "//tools/adt/idea:android/lib/commons-compress-1.8.1",
          "//tools/idea:lib/gson-2.5",
          "//tools/base/repository:repository",
      "//tools/base/testutils:testutils",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/build-system:builder-test-api",
          "//tools/base/ddmlib:ddmlib",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcpkix-jdk15on/1.48/bcpkix-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "sdk-common_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/sdk-common:sdk-common",
          "//tools/base/layoutlib-api:layoutlib-api",
              "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
              "//tools/idea:lib/hamcrest-core-1.3",
              "//tools/idea:lib/junit-4.12",
              "//tools/base/annotations:android-annotations",
          "//tools/base/common:common",
              "//tools/idea:lib/guava-18.0",
          "//tools/base/sdklib:sdklib",
              "//tools/base/device_validator:dvlib",
              "//tools/idea:lib/httpcore-4.4.1",
              "//tools/idea:lib/httpmime-4.4.1",
              "//tools/idea:lib/httpclient-4.4.1",
              "//tools/idea:lib/fluent-hc-4.4.1",
              "//tools/adt/idea:android/lib/commons-compress-1.8.1",
              "//tools/idea:lib/gson-2.5",
              "//tools/base/repository:repository",
          "//prebuilts/tools/common/builder-model:builder-model-2.2.0-dev",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/common:common_testlib",
      "//tools/base/sdklib:sdklib_testlib",
          "//tools/base/device_validator:dvlib_testlib",
          "//tools/base/repository:repository_testlib",
      "//tools/base/testutils:testutils",
      "//tools/base/testutils:testutils_testlib",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/build-system:builder-test-api",
          "//tools/base/ddmlib:ddmlib",
      "//tools/base/build-system:builder-test-api_testlib",
          "//tools/base/ddmlib:ddmlib_testlib",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcpkix-jdk15on/1.48/bcpkix-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_test(
  name = "sdk-common_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":sdk-common_testlib",
      "//tools/base/bazel:test_runner",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=sdk-common_testlib.jar",
    ],
  test_class = "com.android.tools.BazelTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "sdk-common.res",
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
      "src/main/java": "sdk-common.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)
