# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    deps = [
        "//tools/adt/idea:android/lib/asm-5.0.3",
        "//tools/adt/idea:android/lib/asm-analysis-5.0.3",
        "//tools/adt/idea:android/lib/asm-tree-5.0.3",
        "//prebuilts/tools/common/m2/repository/com/android/tools/external/lombok/lombok-ast/0.2.3:jar",
        "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
        "//tools/base/annotations:android-annotations[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools/base/common:common[module]",
        "//tools/base/sdklib:sdklib[module]",
        "//tools/base/sdk-common:sdk-common[module]",
        "//tools/base/layoutlib-api:layoutlib-api[module]",
        "//tools:idea.java-psi-api[module]",
    ],
    exports = [
        "//tools/adt/idea:android/lib/asm-5.0.3",
        "//tools/adt/idea:android/lib/asm-analysis-5.0.3",
        "//tools/adt/idea:android/lib/asm-tree-5.0.3",
        "//prebuilts/tools/common/m2/repository/com/android/tools/external/lombok/lombok-ast/0.2.3:jar",
        "//prebuilts/tools/common/builder-model:builder-model-2.2.0-alpha6",
        "//tools/base/annotations:android-annotations",
        "//tools:idea/lib/guava-18.0",
        "//tools/base/sdk-common:sdk-common",
        "//tools/base/layoutlib-api:layoutlib-api",
        "//tools:idea.java-psi-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    deps = ["//tools/base/lint:lint-api[module]"],
    exports = ["//tools/base/lint:lint-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
