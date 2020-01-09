load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# Profiler-specific additions to the transport test framework.
kotlin_library(
    name = "test-framework",
    srcs = ["src/main/java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/test-framework",
        "//tools/idea/.idea/libraries:truth",
    ],
)
