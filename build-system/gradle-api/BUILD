load("//tools/base/bazel:kotlin.bzl", "kotlin_test", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "gradle-api_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.build:gradle-api:" + BUILD_VERSION,
    legacy_name = "gradle-api",
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/gradle-api",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/build-system/builder-model:builder-model_bzl",  # Only present for legacy reasons.
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_bzl",
        "@maven//:com.google.guava.guava",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.ow2.asm.asm",
    ],
)

kotlin_test(
    name = "tests",
    size = "medium",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "@maven//:org.codehaus.groovy.groovy-all",  # required by //tools/base/build-system:gradle-api
    ],
    deps = [
        ":gradle-api",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/builder-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:nl.jqno.equalsverifier.equalsverifier",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.ow2.asm.asm",
    ],
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
