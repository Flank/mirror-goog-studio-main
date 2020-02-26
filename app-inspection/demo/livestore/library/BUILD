load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "livestore",
    srcs = glob(["src/**/*.kt"]),
    deps = [
        "//tools/base/app-inspection/demo/livestore/protocol:livestore-protocol",
    ],
)

kotlin_test(
    name = "livestore_tests",
    srcs = glob(["testSrc/**/*.kt"]),
    test_class = "com.android.tools.appinspection.livestore.LiveStoreTestSuite",
    deps = [
        ":livestore",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
