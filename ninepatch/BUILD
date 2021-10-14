load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.ninepatch",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.ninepatch.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = [
        "src/test/java",
        "src/test/resources",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

maven_library(
    name = "tools.ninepatch",
    srcs = glob(["src/main/java/**/*.java"]),
    coordinates = "com.android.tools:ninepatch:" + BASE_VERSION,
    description = "draws a bitmap in nine or more sections",
    notice = "NOTICE",
    pom_name = "NinePatch",
    resource_strip_prefix = "tools/base/draw9patch/src/main/resources",
    visibility = ["//visibility:public"],
    deps = ["@maven//:com.google.guava.guava"],
)

kotlin_test(
    name = "tools.ninepatch.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.ninepatch.tests.jar"],
    resources = glob(["src/test/resources/**/*"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.ninepatch",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
