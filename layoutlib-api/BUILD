load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.layoutlib-api",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/annotations:studio.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)

maven_java_library(
    name = "tools.layoutlib-api",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.intellij_annotations",
        "//tools/base/third_party:net.sf.kxml_kxml2",
    ],
)

maven_pom(
    name = "pom",
    artifact = "layoutlib-api",
    group = "com.android.tools.layoutlib",
    source = "//tools/buildSrc/base:base_version",
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.layoutlib-api",
        "//tools/base/third_party:junit_junit",
    ],
)
