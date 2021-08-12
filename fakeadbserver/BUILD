load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "maven_library", "kotlin_test")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.fakeadbserver",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.fakeadbserver.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

maven_library(
    name = "tools.fakeadbserver_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.fakeadbserver:fakeadbserver:" + BASE_VERSION,
    legacy_name = "tools.fakeadbserver",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/fakeadbserver",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/common:common_bzl",
        "@maven//:com.google.guava.guava",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.fakeadbserver",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
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
