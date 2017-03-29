load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/idea/.idea/libraries:lombok-ast",
        "//tools/base/annotations:studio.android-annotations",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/build-system/builder-model:studio.builder-model",
        "//tools/base/sdk-common:studio.sdk-common",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/idea/java/java-psi-api",
        "//tools/base/build-system:studio.manifest-merger",
        "//tools/idea/.idea/libraries:uast",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/idea/.idea/libraries:lombok-ast",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/build-system/builder-model:studio.builder-model[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools/idea/.idea/libraries:uast",
    ],
)

maven_java_library(
    name = "tools.lint-api",
    # TODO: move resources out of java?
    srcs = glob(["libs/lint-api/src/main/java/**/*.java"]),
    pom = "lint-api.pom",
    resources = glob(
        include = ["libs/lint-api/src/main/java/**"],
        exclude = ["libs/lint-api/src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/intellij-core:intellij-core-171.3780.107",
        "//prebuilts/tools/common/uast:uast-171.3780.107",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

maven_pom(
    name = "lint-api.pom",
    artifact = "lint-api",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

iml_module(
    name = "studio.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.lint-api"],
    deps = ["//tools/base/lint:studio.lint-api[module]"],
)

maven_java_library(
    name = "tools.lint-checks",
    # TODO: move resources out of java?
    srcs = glob(["libs/lint-checks/src/main/java/**/*.java"]),
    pom = "lint-checks.pom",
    resources = glob(
        include = ["libs/lint-checks/src/main/java/**"],
        exclude = ["libs/lint-checks/src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        "//prebuilts/tools/common/intellij-core:intellij-core-171.3780.107",
        "//prebuilts/tools/common/uast:uast-171.3780.107",
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
    ],
)

maven_pom(
    name = "lint-checks.pom",
    artifact = "lint-checks",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)
