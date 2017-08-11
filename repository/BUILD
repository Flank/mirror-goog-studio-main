load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.repository",
    srcs = ["src/main/java"],
    iml_files = ["repository.iml"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:jimfs",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:mockito",
    ],
)

maven_java_library(
    name = "tools.repository",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["src/main/java/**/*.java"]),
    pom = ":pom",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = ["src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    # TODO: Why is MockFileOp in main sources?
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:org.apache.commons_commons-compress",
    ],
)

maven_pom(
    name = "pom",
    artifact = "repository",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.repository",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.commons_commons-compress",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
