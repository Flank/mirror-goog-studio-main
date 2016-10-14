load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.repository",
    srcs = ["src/main/java"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:jimfs",
    ],
)

java_library(
    name = "tools.repository",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["src/main/java/**/*.java"]),
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

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    javacopts = ["-extra_checks:off"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.repository",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.commons_commons-compress",
    ],
)
