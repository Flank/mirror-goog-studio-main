load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.testutils",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:easymock-tools",
        "//tools/idea/.idea/libraries:mockito",
        "//tools/idea/.idea/libraries:truth",
    ],
    exports = ["//tools/base/common:studio.common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.testutils",
    srcs = glob(["src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:org.mockito_mockito-all",
        "//tools/base/third_party:com.google.truth_truth",
    ],
    visibility = ["//visibility:public"],
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    deps = [":tools.testutils"],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
)
