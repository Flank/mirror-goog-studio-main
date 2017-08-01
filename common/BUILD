load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.common",
    srcs = ["src/main/java"],
    bundle_data = [
        "src/main/java/com/android/SdkConstants.java",
    ],
    iml_files = ["common.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/annotations:studio.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

maven_java_library(
    name = "tools.common",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

maven_pom(
    name = "pom",
    artifact = "common",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.common",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
