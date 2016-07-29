# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_library(
  name = "lint-api",
  srcs = glob([
      "libs/lint-api/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/lint/lint-api.resources",
  resources = [
      "//tools/base/lint:lint-api.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm/5.0.3/asm-5.0.3",
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm-analysis/5.0.3/asm-analysis-5.0.3",
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm-tree/5.0.3/asm-tree-5.0.3",
      "//prebuilts/tools/common/m2:repository/com/android/tools/external/lombok/lombok-ast/0.2.3/lombok-ast-0.2.3",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/guava-18.0",
      "//tools/base/common:common",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/idea:java-psi-api",
    ],
  exports = [
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm/5.0.3/asm-5.0.3",
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm-analysis/5.0.3/asm-analysis-5.0.3",
      "//prebuilts/tools/common/m2:repository/org/ow2/asm/asm-tree/5.0.3/asm-tree-5.0.3",
      "//prebuilts/tools/common/m2:repository/com/android/tools/external/lombok/lombok-ast/0.2.3/lombok-ast-0.2.3",
      "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/guava-18.0",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/idea:java-psi-api",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "lint-checks.res",
  srcs = glob([
      "libs/lint-checks/src/main/java/**/*",
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
      "libs/lint-checks/src/main/java": "lint-checks.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "lint-api.res",
  srcs = glob([
      "libs/lint-api/src/main/java/**/*",
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
      "libs/lint-api/src/main/java": "lint-api.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "lint-checks",
  srcs = glob([
      "libs/lint-checks/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/base/lint/lint-checks.resources",
  resources = [
      "//tools/base/lint:lint-checks.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/lint:lint-api",
    ],
  exports = [
      "//tools/base/lint:lint-api",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
