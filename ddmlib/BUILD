# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.ddmlib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:easymock-tools[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = [
        "//tools/base/common:studio.common",
        "//tools/idea/.idea/libraries:kxml2",
    ],
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.ddmlib",
    srcs = glob(["src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
    ],
    visibility = ["//visibility:public"],
    javacopts = ["-extra_checks:off"],
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    deps = [
        ":tools.ddmlib",
        "//tools/base/annotations",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:junit_junit",
        "//tools/base/testutils:tools.testutils",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    data = ["//prebuilts/studio/sdk:platform-tools"],
)
