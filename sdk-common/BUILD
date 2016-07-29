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
      "//tools/base/common:common",
      "//tools/base/sdklib:sdklib",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/testutils:testutils",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/build-system:builder-test-api",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcpkix-jdk15on/1.48/bcpkix-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  exports = [
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/common:common",
      "//tools/base/sdklib:sdklib",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//tools/idea:lib/gson-2.5",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "sdk-common_testlib.res",
  srcs = glob([
      "src/test/java/**/*",
      "src/test/resources/**/*",
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
      "src/test/java": "sdk-common_testlib.resources",
      "src/test/resources": "sdk-common_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "sdk-common_testlib",
  srcs = glob([
      "src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/sdk-common/sdk-common_testlib.resources",
  resources = [
      "//tools/base/sdk-common:sdk-common_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//tools/base/testutils:testutils",
      "//tools/base/testutils:testutils_testlib",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/idea:lib/gson-2.5",
      "//tools/base/build-system:builder-test-api",
      "//tools/base/build-system:builder-test-api_testlib",
      "//prebuilts/tools/common/easymock-tools:easymock-3.1",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcpkix-jdk15on/1.48/bcpkix-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
    ],
  exports = [
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
      "//prebuilts/tools/common/kxml2-tools:kxml2-2.3.0",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//tools/idea:lib/gson-2.5",
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
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=sdk-common_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
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
