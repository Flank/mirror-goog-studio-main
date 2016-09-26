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

java_library(
    name = "tools.lint-api",
    # TODO: move resources out of java?
    srcs = glob(["libs/lint-api/src/main/java/**/*.java"]),
    resources = glob(
        ["libs/lint-api/src/main/java/**"],
        exclude = ["libs/lint-api/src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.com-intellij_uast",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

iml_module(
    name = "studio.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    deps = ["//tools/base/lint:studio.lint-api[module]"],
    exports = ["//tools/base/lint:studio.lint-api"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.lint-checks",
    # TODO: move resources out of java?
    srcs = glob(["libs/lint-checks/src/main/java/**/*.java"]),
    resources = glob(
        ["libs/lint-checks/src/main/java/**"],
        exclude = ["libs/lint-checks/src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.com-intellij_uast",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
    ],
)
