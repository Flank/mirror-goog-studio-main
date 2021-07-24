load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library_legacy", "kotlin_test")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

kotlin_library_legacy(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    lint_baseline = "lint_baseline.xml",
    pom = ":pom",
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
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.intellij.deps_trove4j",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "pom",
    artifact = "lint",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
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
    ]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
