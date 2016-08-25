# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    deps = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/idea/.idea/libraries:lombok-ast",
        "//tools/idea/.idea/libraries:builder-model",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/base/common:studio.common[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools:idea.java-psi-api[module]",
    ],
    exports = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/idea/.idea/libraries:lombok-ast",
        "//tools/idea/.idea/libraries:builder-model",
        "//tools/base/annotations:studio.android-annotations",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/base/sdk-common:studio.sdk-common",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools:idea.java-psi-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "studio.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    deps = ["//tools/base/lint:studio.lint-api[module]"],
    exports = ["//tools/base/lint:studio.lint-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
