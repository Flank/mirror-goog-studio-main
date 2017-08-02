load("//tools/base/bazel:bazel.bzl", "iml_module")

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
    javacopts = [
        "-source 6",
        "-target 6",
    ],
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
