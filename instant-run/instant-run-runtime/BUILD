load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.instant-run-runtime",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/instant-run:studio.instant-run-client[module, test]",
        "//tools/idea/.idea/libraries:guava-tools[test]",
    ],
)

java_library(
    name = "instant-run-runtime",
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/instant-run:instant-run-common",
    ],
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":instant-run-runtime",
        "//tools/base/instant-run:instant-run-client",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
