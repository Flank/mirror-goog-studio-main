load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint:" + BASE_VERSION,
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
    exports = [
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
    ],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
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
