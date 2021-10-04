load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.draw9patch",
    # do not sort: must match IML order
    srcs = [
        "src/main/java",
        "src/main/resources",
    ],
    iml_files = ["android.sdktools.draw9patch.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

maven_library(
    name = "tools.draw9patch",
    srcs = glob(["src/main/java/**/*.java"]),
    coordinates = "com.android.tools:draw9patch:" + BASE_VERSION,
    description = "WYSIWYG editor for automatically resizing images",
    notice = "NOTICE",
    pom_name = "Draw 9-patch tool",
    resource_strip_prefix = "tools/base/draw9patch/src/main/resources",
    resources = glob(["src/main/resources/**/*"]),
    visibility = ["//visibility:public"],
)

kotlin_test(
    name = "tools.draw9patch.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.draw9patch.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.draw9patch",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
