load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# Profiler-specific additions to the transport test framework.
kotlin_library(
    name = "test-framework",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//tools/base/transport/test-framework",
    ],
)
