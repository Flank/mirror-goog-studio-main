load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "test-framework",
    testonly = True,
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/base/transport/test-framework",
        "@maven//:androidx.annotation.annotation",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)
