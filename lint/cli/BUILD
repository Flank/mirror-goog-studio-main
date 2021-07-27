load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test", "maven_library")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "cli_bzl",
    # TODO: move resources out of java?
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint:" + BASE_VERSION,
    legacy_name = "cli",
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/lint/cli",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = [
            "src/main/java/**/*.java",
            "src/main/java/**/*.kt",
        ],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core:intellij-core_bzl",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler:kotlin-compiler_bzl",
        "//prebuilts/tools/common/lint-psi/uast:uast_bzl",
        "//tools/analytics-library/protos/src/main/proto:proto_bzl",
        "//tools/analytics-library/shared:tools.analytics-shared_bzl",
        "//tools/analytics-library/tracker:tools.analytics-tracker_bzl",
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/build-system:tools.manifest-merger_bzl",
        "//tools/base/build-system/builder-model:builder-model_bzl",
        "//tools/base/common:common_bzl",
        "//tools/base/layoutlib-api:tools.layoutlib-api_bzl",
        "//tools/base/lint:tools.lint-api_bzl",
        "//tools/base/lint:tools.lint-checks_bzl",
        "//tools/base/lint:tools.lint-model_bzl",
        "//tools/base/repository:tools.repository_bzl",
        "//tools/base/sdk-common:tools.sdk-common_bzl",
        "//tools/base/sdklib:tools.sdklib_bzl",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
)

sdk_java_binary(
    name = "lint",
    default_jvm_opts = {
        "linux": "-Xmx1024m -Dcom.android.tools.lint.bindir=$APP_HOME",
        "win": "-Xmx1024m -Dcom.android.tools.lint.bindir=%~dp0\\..",
        "mac": "-Xmx1024m -Dcom.android.tools.lint.bindir=$APP_HOME",
    },
    main_class = "com.android.tools.lint.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":cli"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
