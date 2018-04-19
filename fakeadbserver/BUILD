load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.android.sdktools.fakeadbserver",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.fakeadbserver.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

kotlin_library(
    name = "tools.fakeadbserver",
    srcs = ["src/main/java"],
    pom = ":pom",
    resource_strip_prefix = "tools/base/fakeadbserver",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

maven_pom(
    name = "pom",
    artifact = "fakeadbserver",
    group = "com.android.tools.fakeadbserver",
    source = "//tools/buildSrc/base:base_version",
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.fakeadbserver",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)
