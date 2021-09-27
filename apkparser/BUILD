load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.binary-resources",
    srcs = ["binary-resources/src/main/java"],
    iml_files = ["binary-resources/android.sdktools.binary-resources.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["binary-resources/src/test/resources"],
    test_srcs = ["binary-resources/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

maven_library(
    name = "tools.binary-resources",
    srcs = glob(["binary-resources/src/main/java/**"]),
    coordinates = "com.android.tools.apkparser:binary-resources:" + BASE_VERSION,
    description = "Library for parsing the resources.arsc file",
    notice = "binary-resources/NOTICE",
    pom_name = "Binary Resources parser",
    resource_strip_prefix = "tools/base/apkparser/binary-resources",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "@maven//:com.google.guava.guava",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["binary-resources/src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["binary-resources/src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.binary-resources",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)
