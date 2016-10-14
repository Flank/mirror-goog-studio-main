load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.layoutlib-api",
    srcs = ["src/main/java"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/annotations:studio.android-annotations",
    ],
    deps = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools:idea.annotations[module]",
    ],
)

java_library(
    name = "tools.layoutlib-api",
    srcs = glob(["src/main/java/**"]),
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.intellij_annotations",
        "//tools/base/third_party:net.sf.kxml_kxml2",
    ],
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
