load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.layoutlib-api",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.layoutlib-api.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

maven_library(
    name = "tools.layoutlib-api_bzl",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.layoutlib:layoutlib-api:" + BASE_VERSION,
    legacy_name = "tools.layoutlib-api",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/layoutlib-api",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/common:common_bzl",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.annotations",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.layoutlib-api",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
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
