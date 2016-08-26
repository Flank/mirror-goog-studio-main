load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.layoutlib-api",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools:idea.annotations[module]",
    ],
    exports = [
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/annotations:studio.android-annotations",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.layoutlib-api",
    srcs = glob(["src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.intellij_annotations",
        "//tools/base/third_party:net.sf.kxml_kxml2",
    ],
    visibility = ["//visibility:public"],
    javacopts = ["-extra_checks:off"],
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    deps = [
        ":tools.layoutlib-api",
        "//tools/base/third_party:junit_junit",
    ],
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
)
