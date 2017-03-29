load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob(["src/main/java/**/*.java"]),
    pom = ":pom",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = ["src/main/java/**/*.java"],
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
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.external.lombok_lombok-ast",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-runtime",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
    ],
)

maven_pom(
    name = "pom",
    artifact = "lint",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)
