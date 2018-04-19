load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.binary-resources",
    srcs = ["binary-resources/src/main/java"],
    iml_files = ["binary-resources/android.sdktools.binary-resources.iml"],
    test_resources = ["binary-resources/src/test/resources"],
    test_srcs = ["binary-resources/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

java_library(
    name = "tools.binary-resources",
    srcs = glob(["binary-resources/src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

java_test(
    name = "tools.tests",
    srcs = glob(["binary-resources/src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["binary-resources/src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.binary-resources",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
